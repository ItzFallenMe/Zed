package com.zed.assistant.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.SpeakerModel

/**
 * Loads the bundled offline Vosk models exactly once. The 83 MB of assets are
 * unpacked into app-private storage on first launch because Vosk reads model
 * files from the filesystem, not from the APK asset manager.
 */
object VoskModelManager {

  private const val TAG = "ZedVosk"
  private const val SPEECH_ASSET = "model-en-us"
  private const val SPEAKER_ASSET = "model-spk"

  private val _isModelReady = MutableStateFlow(false)
  val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

  private val _isSpeakerModelReady = MutableStateFlow(false)
  val isSpeakerModelReady: StateFlow<Boolean> = _isSpeakerModelReady.asStateFlow()

  @Volatile
  var model: Model? = null
    private set

  @Volatile
  var speakerModel: SpeakerModel? = null
    private set

  private val initializing = AtomicBoolean(false)

  fun init(context: Context, scope: CoroutineScope) {
    if (model != null && speakerModel != null) return
    if (!initializing.compareAndSet(false, true)) return

    scope.launch(Dispatchers.IO) {
      try {
        val speechDir = File(context.filesDir, "vosk-model")
        ensureUnpacked(context.assets, SPEECH_ASSET, speechDir, "am/final.mdl")
        val loaded = Model(speechDir.absolutePath)
        model = loaded
        _isModelReady.value = true
        Log.i(TAG, "Speech model ready")

        try {
          val speakerDir = File(context.filesDir, "vosk-spk-model")
          ensureUnpacked(context.assets, SPEAKER_ASSET, speakerDir, "final.ext.raw")
          speakerModel = SpeakerModel(speakerDir.absolutePath)
          _isSpeakerModelReady.value = true
          Log.i(TAG, "Speaker model ready")
        } catch (error: Throwable) {
          Log.e(TAG, "Speaker model unavailable; owner-voice checks are disabled", error)
        }
      } catch (error: Throwable) {
        Log.e(TAG, "Could not load Vosk model", error)
      } finally {
        initializing.set(false)
      }
    }
  }

  private fun ensureUnpacked(assets: AssetManager, assetDir: String, target: File, marker: String) {
    val markerFile = File(target, marker)
    if (markerFile.isFile && markerFile.length() > 0L) return
    Log.i(TAG, "Unpacking $assetDir to ${target.absolutePath}")
    target.deleteRecursively()
    target.mkdirs()
    copyAssetFolder(assets, assetDir, target)
  }

  private fun copyAssetFolder(assets: AssetManager, from: String, to: File) {
    val children = assets.list(from)
    if (children.isNullOrEmpty()) {
      to.parentFile?.mkdirs()
      assets.open(from).use { input ->
        FileOutputStream(to).use { output -> input.copyTo(output) }
      }
      return
    }
    to.mkdirs()
    for (child in children) {
      copyAssetFolder(assets, "$from/$child", File(to, child))
    }
  }
}
