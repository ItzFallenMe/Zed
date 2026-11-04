package com.zed.assistant.ui

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zed.assistant.ai.PermissionGateway
import com.zed.assistant.audio.SpeakerVerifier
import com.zed.assistant.automation.ZedAccessibilityService
import com.zed.assistant.audio.VoiceCapture
import com.zed.assistant.audio.VoskModelManager
import com.zed.assistant.data.Memory
import com.zed.assistant.data.MemoryStore
import com.zed.assistant.data.ZedPreferences
import com.zed.assistant.ui.theme.LocalZedPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Owner-voice enrolment. Three short samples are converted to Vosk speaker
 * vectors on-device; only the vectors are stored, never the recordings.
 */
@Composable
fun OwnerVoiceDialog(
  preferences: ZedPreferences,
  onDismiss: () -> Unit,
  onProfileChanged: (Boolean) -> Unit
) {
  val palette = LocalZedPalette.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var vectors by remember { mutableStateOf(preferences.getOwnerVoiceSamples()) }
  var recording by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val active = preferences.isOwnerVoiceEnabled && preferences.hasOwnerVoiceProfile()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Owner voice", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          if (active) {
            "Your speaker profile is active on this device. When enabled, the wake word opens only for your enrolled voice. Manual taps remain available."
          } else {
            "Record three short samples of your normal speaking voice. The model runs fully on-device; recordings are discarded after a speaker vector is extracted."
          },
          color = palette.textMuted,
          fontSize = 12.sp,
          lineHeight = 18.sp
        )
        Text(
          "${vectors.size.coerceAtMost(3)} of 3 samples captured",
          color = palette.accent,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        if (recording) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = palette.accent,
              strokeWidth = 2.dp
            )
            Text(
              "Recording for four seconds…",
              color = palette.textMuted,
              fontSize = 11.sp,
              modifier = Modifier.padding(start = 10.dp)
            )
          }
        } else {
          TextButton(
            onClick = {
              scope.launch {
                recording = true
                error = null
                val granted = PermissionGateway.ensure(
                  context,
                  Manifest.permission.RECORD_AUDIO
                )
                if (!granted) {
                  error = "Microphone permission is required."
                  recording = false
                  return@launch
                }

                VoskModelManager.init(context, scope)
                val ready = withTimeoutOrNull(60_000L) {
                  VoskModelManager.isSpeakerModelReady.first { it }
                } ?: false
                if (!ready) {
                  error = "The on-device speaker model isn't ready yet. Try again in a moment."
                  recording = false
                  return@launch
                }

                val pcm = withContext(Dispatchers.IO) { VoiceCapture.record(4) }
                if (pcm == null) {
                  error = "I couldn't record that sample. Check the microphone and try again."
                  recording = false
                  return@launch
                }
                val vector = withContext(Dispatchers.Default) { SpeakerVerifier.extract(pcm) }
                if (vector == null) {
                  error = "That sample was too quiet or unclear. Try again and speak normally."
                  recording = false
                  return@launch
                }

                val updated = vectors + vector
                vectors = updated
                if (updated.size >= 3) {
                  val centroid = SpeakerVerifier.centroid(updated)
                  if (centroid != null) {
                    preferences.saveOwnerVoiceProfile(centroid, updated)
                    onProfileChanged(true)
                  } else {
                    error = "Could not build the voice profile."
                  }
                }
                recording = false
              }
            }
          ) {
            Text(
              if (vectors.isEmpty()) "RECORD FIRST SAMPLE" else "ADD ANOTHER SAMPLE",
              color = palette.accent,
              fontWeight = FontWeight.Bold
            )
          }
        }
        if (error != null) {
          Text(error!!, color = palette.error, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text(
          "The profile is optional. Creator-code verification remains the only way to unlock master control.",
          color = palette.textMuted,
          fontSize = 10.sp,
          lineHeight = 15.sp
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("DONE", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      if (active || vectors.isNotEmpty()) {
        TextButton(
          onClick = {
            preferences.clearOwnerVoiceProfile()
            vectors = emptyList()
            onProfileChanged(false)
          }
        ) {
          Text("REMOVE PROFILE", color = palette.error, fontWeight = FontWeight.Bold)
        }
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
fun OverlayStyleDialog(
  current: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val palette = LocalZedPalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Overlay avatar", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          "The robot head is the primary Zed form. The other shapes are optional skins for the floating overlay.",
          color = palette.textMuted,
          fontSize = 11.sp,
          lineHeight = 16.sp,
          modifier = Modifier.padding(bottom = 8.dp)
        )
        ZedPreferences.OVERLAY_TOYS.forEach { toy ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelect(toy) }
              .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = toyLabel(toy),
              color = if (toy == current) palette.accent else palette.textPrimary,
              fontSize = 13.sp,
              modifier = Modifier.weight(1f)
            )
            if (toy == current) {
              Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = palette.accent,
                modifier = Modifier.size(17.dp)
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("CLOSE", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
fun MemoryDialog(onDismiss: () -> Unit) {
  val palette = LocalZedPalette.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val store = remember { MemoryStore(context) }
  var memories by remember { mutableStateOf<List<Memory>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }

  fun refresh() {
    scope.launch {
      memories = withContext(Dispatchers.IO) { store.all(60) }
      loading = false
    }
  }

  LaunchedEffect(Unit) { refresh() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Memory vault", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          "Facts Zed saved because you asked it to remember them. Nothing here is inferred or uploaded.",
          color = palette.textMuted,
          fontSize = 11.sp,
          lineHeight = 16.sp
        )
        if (loading) {
          Text("Loading…", color = palette.textMuted, fontSize = 12.sp)
        } else if (memories.isEmpty()) {
          Text(
            "No saved memories yet.",
            color = palette.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 14.dp)
          )
        } else {
          memories.forEach { memory ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(memory.text, color = palette.textPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                Text(
                  memory.category.replaceFirstChar { it.uppercase(Locale.ROOT) },
                  color = palette.textMuted,
                  fontSize = 9.sp,
                  modifier = Modifier.padding(top = 2.dp)
                )
              }
              TextButton(
                onClick = {
                  scope.launch {
                    withContext(Dispatchers.IO) { store.delete(memory.id) }
                    refresh()
                  }
                }
              ) {
                Icon(
                  Icons.Rounded.Close,
                  contentDescription = "Forget this memory",
                  tint = palette.error,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (memories.isNotEmpty()) {
        TextButton(
          onClick = {
            scope.launch {
              withContext(Dispatchers.IO) { store.clear() }
              refresh()
            }
          }
        ) {
          Text("CLEAR ALL", color = palette.error, fontWeight = FontWeight.Bold)
        }
      } else {
        TextButton(onClick = onDismiss) {
          Text("CLOSE", color = palette.accent, fontWeight = FontWeight.Bold)
        }
      }
    },
    dismissButton = {
      if (memories.isNotEmpty()) {
        TextButton(onClick = onDismiss) {
          Text("CLOSE", color = palette.textMuted, fontWeight = FontWeight.Bold)
        }
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
fun PermissionsDialog(onDismiss: () -> Unit) {
  val palette = LocalZedPalette.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val packageUri = Uri.parse("package:${context.packageName}")

  val microphone = granted(context, Manifest.permission.RECORD_AUDIO)
  val contacts = granted(context, Manifest.permission.READ_CONTACTS)
  val location = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
    granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
  val notifications = Build.VERSION.SDK_INT < 33 ||
    granted(context, Manifest.permission.POST_NOTIFICATIONS)
  val overlay = Settings.canDrawOverlays(context)
  val accessibility = accessibilityEnabled(context)
  val writeSettings = Settings.System.canWrite(context)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Permissions & access", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          "Zed asks only when a feature needs it. You can revoke any of these in Android settings.",
          color = palette.textMuted,
          fontSize = 11.sp,
          lineHeight = 16.sp,
          modifier = Modifier.padding(bottom = 8.dp)
        )
        PermissionRow(
          title = "Microphone",
          granted = microphone,
          onClick = {
            scope.launch {
              PermissionGateway.ensure(context, Manifest.permission.RECORD_AUDIO)
              onDismiss()
            }
          }
        )
        PermissionRow(
          title = "Contacts",
          granted = contacts,
          onClick = {
            scope.launch {
              PermissionGateway.ensure(context, Manifest.permission.READ_CONTACTS)
              onDismiss()
            }
          }
        )
        PermissionRow(
          title = "Location (weather)",
          granted = location,
          onClick = {
            scope.launch {
              PermissionGateway.ensure(context, Manifest.permission.ACCESS_COARSE_LOCATION)
              onDismiss()
            }
          }
        )
        PermissionRow(
          title = "Notifications",
          granted = notifications,
          onClick = {
            context.startActivity(
              Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
            onDismiss()
          }
        )
        PermissionRow(
          title = "Display over other apps",
          granted = overlay,
          onClick = {
            context.startActivity(
              Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            )
            onDismiss()
          }
        )
        PermissionRow(
          title = "Accessibility screen control",
          granted = accessibility,
          onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            onDismiss()
          }
        )
        PermissionRow(
          title = "Change brightness",
          granted = writeSettings,
          onClick = {
            context.startActivity(
              Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri)
            )
            onDismiss()
          }
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("CLOSE", color = palette.accent, fontWeight = FontWeight.Bold)
      }
    },
    containerColor = palette.card,
    shape = RoundedCornerShape(22.dp)
  )
}

@Composable
private fun PermissionRow(
  title: String,
  granted: Boolean,
  onClick: () -> Unit
) {
  val palette = LocalZedPalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
    Icon(
      if (granted) Icons.Rounded.Check else Icons.Rounded.Shield,
      contentDescription = if (granted) "Granted" else "Not granted",
      tint = if (granted) palette.success else palette.textMuted,
      modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.size(6.dp))
    Text(
      if (granted) "Allowed" else "Open",
      color = if (granted) palette.success else palette.accent,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

private fun granted(context: Context, permission: String): Boolean =
  ContextCompat.checkSelfPermission(context, permission) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED

private fun accessibilityEnabled(context: Context): Boolean =
  ZedAccessibilityService.isEnabledInSettings(context)

private fun toyLabel(toy: String): String = when (toy) {
  "robot-head" -> "Robot head (primary)"
  else -> toy.replaceFirstChar { it.uppercase(Locale.ROOT) }
}
