package com.zed.assistant.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.zed.assistant.ai.PermissionGateway
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Live weather from Open-Meteo (no API key). Location is read only on request
 * and only with the user's runtime permission; there is no hard-coded city and
 * no IP-based fallback.
 */
class WeatherRepository(private val context: Context) {

  private val client = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .build()

  suspend fun current(): JSONObject = withContext(Dispatchers.IO) {
    if (!hasLocationPermission()) {
      val granted = PermissionGateway.ensure(context, Manifest.permission.ACCESS_COARSE_LOCATION)
      if (!granted) return@withContext failure("location_permission_denied")
    }

    val location = bestLocation() ?: return@withContext failure("location_unavailable")
    val city = cityName(location.latitude, location.longitude)

    val url = "https://api.open-meteo.com/v1/forecast" +
      "?latitude=${location.latitude}&longitude=${location.longitude}" +
      "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code" +
      "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"

    try {
      client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) return@withContext failure("weather_service_unavailable")
        val body = response.body?.string() ?: return@withContext failure("weather_service_unavailable")
        val json = JSONObject(body)
        val current = json.optJSONObject("current")
          ?: return@withContext failure("weather_service_unavailable")
        val temp = current.optDouble("temperature_2m", Double.NaN)
        if (temp.isNaN()) return@withContext failure("weather_service_unavailable")
        val feels = current.optDouble("apparent_temperature", temp)
        val humidity = current.optInt("relative_humidity_2m", 0)
        val condition = conditionFor(current.optInt("weather_code", -1))
        val daily = json.optJSONObject("daily")
        val high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, temp) ?: temp
        val low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, temp) ?: temp

        val summary = "$condition, ${temp.roundCelsius()} in $city. " +
          "Feels like ${feels.roundCelsius()}, high ${high.roundCelsius()}, " +
          "low ${low.roundCelsius()}, humidity $humidity percent."

        JSONObject()
          .put("success", true)
          .put("action", "getWeather")
          .put("city", city)
          .put("condition", condition)
          .put("temperatureC", temp.roundCelsius())
          .put("feelsLikeC", feels.roundCelsius())
          .put("highC", high.roundCelsius())
          .put("lowC", low.roundCelsius())
          .put("humidity", humidity)
          .put("summary", summary)
      }
    } catch (error: Exception) {
      Log.e(TAG, "Weather lookup failed", error)
      failure("weather_service_unavailable")
    }
  }

  private fun hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED

  @SuppressLint("MissingPermission")
  private suspend fun bestLocation(): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(
      LocationManager.GPS_PROVIDER,
      LocationManager.NETWORK_PROVIDER,
      LocationManager.PASSIVE_PROVIDER
    )
    val lastKnown = providers.mapNotNull { provider ->
      runCatching {
        if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
      }.getOrNull()
    }.maxByOrNull { it.time }

    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 15 * 60_000L) {
      return lastKnown
    }

    val fresh = withTimeoutOrNull(4_000L) {
      suspendCancellableCoroutine { continuation ->
        val request = LocationRequestCompat.Builder(1_000L)
          .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
          .setMaxUpdates(1)
          .build()
        val listener = object : LocationListenerCompat {
          override fun onLocationChanged(location: Location) {
            runCatching { LocationManagerCompat.removeUpdates(manager, this) }
            if (continuation.isActive) continuation.resume(location)
          }
        }
        val provider = when {
          manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
          manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
          else -> null
        }
        if (provider == null) {
          if (continuation.isActive) continuation.resume(null)
        } else {
          runCatching {
            LocationManagerCompat.requestLocationUpdates(
              manager, provider, request, listener, Looper.getMainLooper()
            )
          }.onFailure { if (continuation.isActive) continuation.resume(null) }
        }
      }
    }
    return fresh ?: lastKnown
  }

  private fun cityName(latitude: Double, longitude: Double): String {
    return runCatching {
      val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
      addresses?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "your area"
  }

  private fun conditionFor(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "Unknown conditions"
  }

  private fun failure(error: String): JSONObject =
    JSONObject().put("success", false).put("action", "getWeather").put("error", error)

  private fun Double.roundCelsius(): Int = Math.round(this).toInt()

  private companion object {
    const val TAG = "ZedWeather"
  }
}
