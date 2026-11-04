package com.zed.assistant.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.zed.assistant.data.CreatorGate
import java.util.Locale

/**
 * A one-shot, local code prompt. It speaks a short prompt with Android TTS and
 * captures the answer with Android's speech recogniser, never with the Gemini
 * Live session. The captured candidates are handed to [onHeard] for local
 * verification.
 */
class CreatorChallengeSession(
  context: Context,
  private val onHeard: (String) -> Unit,
  private val onUnavailable: (String) -> Unit,
  private val onListening: () -> Unit
) {

  private val appContext = context.applicationContext
  private val main = Handler(Looper.getMainLooper())

  private var recognizer: SpeechRecognizer? = null
  private var tts: TextToSpeech? = null
  private var stopped = false
  private var started = false
  private var listening = false

  fun start(prompt: String) {
    main.post {
      if (stopped || started) return@post
      started = true
      tts = TextToSpeech(appContext) { status ->
        main.post {
          if (stopped) return@post
          if (status == TextToSpeech.SUCCESS) speakThenListen(prompt) else beginListening()
        }
      }
      // Some TTS engines never call back. Never leave the user without a mic.
      main.postDelayed({ if (!listening) beginListening() }, 2_800L)
    }
  }

  fun stop() {
    main.post {
      stopped = true
      main.removeCallbacksAndMessages(null)
      runCatching { recognizer?.cancel() }
      runCatching { recognizer?.destroy() }
      recognizer = null
      runCatching { tts?.stop() }
      runCatching { tts?.shutdown() }
      tts = null
    }
  }

  private fun speakThenListen(prompt: String) {
    val engine = tts ?: run {
      beginListening()
      return
    }
    engine.language = Locale.US
    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) = Unit
      override fun onDone(utteranceId: String?) {
        main.post { beginListening() }
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        main.post { beginListening() }
      }

      override fun onError(utteranceId: String?, errorCode: Int) {
        main.post { beginListening() }
      }
    })
    val queued = engine.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "zed_creator_prompt")
    if (queued != TextToSpeech.SUCCESS) beginListening()
  }

  private fun beginListening() {
    if (stopped || listening) return
    listening = true
    onListening()

    if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
      onUnavailable("Voice entry isn't available on this device. Type the code below.")
      return
    }

    val speech = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }
      .onFailure { Log.e(TAG, "Could not create speech recognizer", it) }
      .getOrNull()
    if (speech == null) {
      onUnavailable("Voice entry couldn't start. Type the code below.")
      return
    }
    recognizer = speech
    speech.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) = Unit
      override fun onBeginningOfSpeech() = Unit
      override fun onRmsChanged(rmsdB: Float) = Unit
      override fun onBufferReceived(buffer: ByteArray?) = Unit
      override fun onEndOfSpeech() = Unit

      override fun onError(error: Int) {
        if (stopped) return
        val message = when (error) {
          SpeechRecognizer.ERROR_NO_MATCH,
          SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch a code. Try again or type it."
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is needed for voice verification."
          SpeechRecognizer.ERROR_NETWORK,
          SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Voice recognition needs a connection. Type the code instead."
          else -> "Voice verification didn't work. Type the code instead."
        }
        onUnavailable(message)
      }

      override fun onResults(results: Bundle?) {
        if (stopped) return
        val candidates = results
          ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          .orEmpty()
        val best = candidates.maxByOrNull { candidate ->
          CreatorGate.normalizeCodeInput(candidate)?.length ?: 0
        }
        if (best.isNullOrBlank() || CreatorGate.normalizeCodeInput(best) == null) {
          onUnavailable("I didn't catch a code. Try again or type it.")
        } else {
          onHeard(best)
        }
      }

      override fun onPartialResults(partialResults: Bundle?) = Unit
      override fun onEvent(eventType: Int, params: Bundle?) = Unit
    })

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(
        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
      )
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
    }
    runCatching { speech.startListening(intent) }
      .onFailure {
        Log.e(TAG, "Could not start local speech recognition", it)
        onUnavailable("Voice entry couldn't start. Type the code below.")
      }
  }

  private companion object {
    const val TAG = "ZedCreatorVoice"
  }
}
