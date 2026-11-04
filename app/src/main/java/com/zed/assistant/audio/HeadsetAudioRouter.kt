package com.zed.assistant.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Routes a communication session to a wired/Bluetooth/USB headset when one is
 * connected, otherwise to the phone's main loudspeaker. The router remembers
 * the pre-session mode and speaker state so a failed route never leaves the
 * device stuck in communication mode.
 */
class HeadsetAudioRouter(context: Context) {

  private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val previousMode = manager.mode
  private val previousSpeakerphone = manager.isSpeakerphoneOn
  private var scoStarted = false

  /** @return a short, human-readable route label for logs and diagnostics. */
  fun route(): String {
    return try {
      manager.mode = AudioManager.MODE_IN_COMMUNICATION
      val headset = findHeadset()
      if (headset != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          manager.setCommunicationDevice(headset)
        } else if (headset.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
          startSco()
        }
        manager.isSpeakerphoneOn = false
        Log.i(TAG, "Routed to headset: ${headset.productName} (type=${headset.type})")
        "headset"
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          manager.clearCommunicationDevice()
        }
        stopSco()
        manager.isSpeakerphoneOn = true
        Log.i(TAG, "Routed to phone loudspeaker")
        "speaker"
      }
    } catch (error: Exception) {
      Log.w(TAG, "Could not select an audio route", error)
      "default"
    }
  }

  fun restore() {
    try {
      stopSco()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        manager.clearCommunicationDevice()
      }
      manager.isSpeakerphoneOn = previousSpeakerphone
      manager.mode = previousMode
    } catch (error: Exception) {
      Log.w(TAG, "Could not restore audio route", error)
    }
  }

  private fun findHeadset(): AudioDeviceInfo? {
    val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      manager.availableCommunicationDevices
    } else {
      manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
    }
    return devices.firstOrNull { device ->
      device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
  }

  @Suppress("DEPRECATION")
  private fun startSco() {
    if (!manager.isBluetoothScoAvailableOffCall || manager.isBluetoothScoOn) return
    manager.startBluetoothSco()
    manager.isBluetoothScoOn = true
    scoStarted = true
  }

  @Suppress("DEPRECATION")
  private fun stopSco() {
    if (!scoStarted) return
    runCatching {
      manager.stopBluetoothSco()
      manager.isBluetoothScoOn = false
    }
    scoStarted = false
  }

  private companion object {
    const val TAG = "ZedAudioRoute"
  }
}
