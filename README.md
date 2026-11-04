# Zed — native Android voice assistant

Zed is a real-time, voice-to-voice assistant built as a native Android app in
Kotlin + Jetpack Compose. The microphone streams raw PCM to the Gemini Live
WebSocket API, and Gemini's native audio output is played through a persistent
`AudioTrack`. There is no browser, no WebView, no `SpeechSynthesis`, no
pre-recorded audio, and no hard-coded reply.

The product UI combines a focused voice orb with a full assistant shell: Home,
Conversation, Voice, Settings, and Updates are available from a floating dock.
The engine, UI state, audio pipeline, and device actions are implemented for
Zed as a single native Android application.

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
- **Complete assistant shell.** The Home dashboard, typed Conversation screen,
  session history, Updates screen, grouped Settings screen, and floating voice
  dock are all backed by the same stateful engine.
- **Typed and spoken turns.** Typed messages use the same Gemini Live session as
  microphone input and completed turns remain available in the current session.
- **Speaker diagnostic.** Settings → *Test speaker* plays 440 Hz through the
  exact `AudioTrack` output graph used by Gemini audio.
- **Foreground session continuity.** An Android foreground service keeps an
  active voice session alive while the user temporarily switches apps.

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
  model/ChatMessage.kt         completed conversation turn model
  ui/                          Compose screens, orb, waveform, navigation dock
    HomeScreen.kt              Home dashboard and quick actions
    ConversationScreen.kt      typed and spoken conversation
    UpdatesScreen.kt            capability and release notes
    NavigationDock.kt           bottom navigation and voice control
  viewmodel/MainViewModel.kt
  ZedSessionService.kt         foreground notification for active sessions
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
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` — only while an
  active voice session is running, with a visible low-priority notification.

No SMS, phone-state, overlay, or accessibility permissions are requested. The
foreground-service permissions are used only while an active voice session is
running.

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
- The foreground service keeps an explicitly started session alive while the
  app is backgrounded. Android may still stop microphone use under system or
  battery policy; Zed reports the resulting disconnect instead of claiming the
  session is still active.
- Emulators often have no real microphone/speaker path; test audio on a physical
  phone.

## Verification

The repository is checked by the Android build workflow on every push to
`main`. Local verification should include:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug :app:lintDebug
```

Audio, Live, and device actions require a physical device with a valid API key
and cannot be fully exercised in a build-only environment. The manual smoke
test is: configure the key, grant microphone permission, start a session, hear
Gemini voice, switch between spoken and typed turns, interrupt playback, test
the speaker, and verify one safe device action.

## Open-source project

Zed is maintained by ItzFallenMe. See [CONTRIBUTING.md](CONTRIBUTING.md) for
the development workflow, [SECURITY.md](SECURITY.md) for responsible reports,
and [LICENSE](LICENSE) for the reuse terms.
