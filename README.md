# Zed — native Android voice assistant

Zed is a real-time, voice-to-voice assistant built as a native Android app in
Kotlin + Jetpack Compose. The microphone streams raw PCM to the Gemini Live
WebSocket API, and Gemini's native audio output is played through a persistent
`AudioTrack`. There is no browser, no WebView, no `SpeechSynthesis`, no
pre-recorded audio, and no hard-coded reply.

The visual language (dark screen, red/purple orb, waveform, mic control) is
adapted from the supplied Myra reference app; the engine, UI state, audio
pipeline and device actions were rewritten for Zed.

## What it does

```
mic (16 kHz PCM16)
  -> Gemini Live BidiGenerateContent
  -> Gemini native audio (24 kHz PCM16)
  -> sequential AudioTrack queue
  -> phone speaker
```

- **Real voice conversation.** Continuous microphone streaming, one persistent
  Live session, native Gemini audio playback, barge-in/interruption support.
  A long-press mutes the current turn until it ends, so stale audio cannot
  resume. Unexpected network drops resume the same conversation when a
  resumption handle is available; otherwise the error card offers a retry.
- **Automatic multilingual conversation.** No language picker. Hindi, English,
  Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam,
  Punjabi, Urdu and every other language the model supports are handled by the
  model's automatic language detection, with the system instruction telling Zed
  to reply in the user's language or mix.
- **Zed personality.** Young, confident, witty, playful, adaptive in tone, never
  robotic, concise enough for speech.
- **Safe device actions.** `openWhatsApp`, `openApp`, `openUrl`, `makeCall`,
  `callContact` — with argument validation, an app allowlist, contact ambiguity
  handling, and truthful tool results.
- **Real UI states.** `IDLE`, `CONNECTING`, `LISTENING`, `SPEAKING`, `ERROR`
  drive the orb, waveform, status text and controls.
- **Speaker diagnostic.** Settings → *Test speaker* plays 440 Hz through the
  exact `AudioTrack` output graph used by Gemini audio.

## Model

The default is **`gemini-3.8-live`**, the current low-latency Live model at the
time of writing. The prompt's preferred `gemini-3.1-flash-live-preview` is now a
legacy model; it is selectable in Settings, along with
`gemini-3.8-live-extended-thinking`.

The Live setup is built for both protocol generations:

- `gemini-3.8-*` tool declarations use `behavior: NON_BLOCKING` and tool
  responses use `scheduling: INTERRUPT`.
- `gemini-3.1-flash-live-preview` uses the synchronous tool protocol (no
  `behavior`/`scheduling`), which that model supports.

## Project layout

```
app/src/main/java/com/zed/assistant/
  MainActivity.kt              activity, permission launcher, navigation
  ai/
    ZedEngine.kt               session state machine, tool routing
    GeminiLiveClient.kt        WebSocket protocol, setup, audio parsing, tools
    AudioEngine.kt             AudioRecord capture + AudioTrack playback queue
    AudioPcm.kt                MIME rate parsing, RMS, diagnostic tone
    DeviceActions.kt           validated allowlisted device actions
    ContactsRepository.kt      READ_CONTACTS name search
    PermissionGateway.kt       coroutine <-> runtime permission bridge
    ZedPersona.kt              Zed system instruction
  model/AssistantState.kt
  ui/                          Compose main screen, orb, waveform, settings
  viewmodel/MainViewModel.kt
```

## Setup

1. Get a Gemini API key from Google AI Studio.
2. Either:
   - enter the key in **Zed → Settings → API key**, or
   - build with `GEMINI_API_KEY=your_key ./gradlew :app:assembleDebug`
     (or `-PGEMINI_API_KEY=your_key`).
3. No key is hard-coded in the repository. The in-app key is stored in this
   app's private `SharedPreferences`.

> For a public release, do not ship a long-lived API key. Use ephemeral Live
> tokens or a backend proxy. The Settings screen says the same thing.

## Build and install

Requirements: JDK 17+, Android SDK platform 36, build-tools 36.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK is also produced by `.github/workflows/build-apk.yml` on pushes to
`main` (artifact `zed-debug-apk`).

## Permissions

Requested lazily, not all at startup:

- `RECORD_AUDIO` — when you first tap the mic.
- `READ_CONTACTS` — only when `callContact` actually needs to search contacts.
- `INTERNET` — Live WebSocket.
- `CALL_PHONE` — requested only at the moment you ask Zed to call someone.
  If granted, calls use `ACTION_CALL`; if denied, Zed opens the system dialer
  with the number (`ACTION_DIAL`) and reports that mode honestly.
- `MODIFY_AUDIO_SETTINGS` — communication audio mode and speaker routing.

No SMS, phone-state, overlay, accessibility, or background-service permissions
are requested.

## Audio correctness details

- Mic: `AudioRecord`, mono, 16-bit PCM, 16 kHz, `VOICE_COMMUNICATION` (falls back
  to `MIC`), acoustic echo cancellation + noise suppression when available.
- Sent as `{"realtimeInput":{"audio":{"mimeType":"audio/pcm;rate=16000","data":...}}}`.
- Received chunks are read from **every** `serverContent.modelTurn.parts[]`
  entry, Base64-decoded to raw bytes, and the sample rate is read from the MIME
  type (default 24 kHz) — never assumed to be text.
- One persistent playback `AudioTrack`, a sequential queue, and a flush on
  `serverContent.interrupted` or long-press interruption.
- Audio is never converted through a WAV header or text; PCM16 LE goes straight
  to the audio hardware.
- Detailed logs use the `ZedLive`, `ZedAudio`, `ZedActions`, `ZedContacts`,
  `ZedPermissions` and `ZedEngine` tags. Base64 audio is not logged.

## Android limitations, stated honestly

- App launching, contact access and calls are Android-native actions; they only
  work on a real Android device, not in a browser.
- WhatsApp must be installed. If it is not, `openWhatsApp` returns
  `whatsapp_not_installed` and Zed says so.
- Contacts can be ambiguous. Zed returns the candidates and asks; it never
  guesses a number.
- If `CALL_PHONE` is not granted, only the dialer can be opened; Zed reports
  `mode: "dialer"` rather than claiming the call connected.
- The session stops when the app leaves the foreground (`onStop`), because this
  build does not implement a microphone foreground service. Adding one would be
  the next step for always-on background use.
- Emulators often have no real microphone/speaker path; test audio on a physical
  phone.

## Verification

`./gradlew clean :app:assembleDebug :app:lintDebug` passes with **0 lint
errors** (17 non-blocking warnings), and the debug APK verifies with
`apksigner`. Audio/Live/device actions require a physical device with a valid
API key and cannot be exercised in this build environment; see the manual test
list in the task description (mic → Live → audible Gemini voice, language
switching, WhatsApp/app open, number call, contact ambiguity, interruption,
speaker test).
