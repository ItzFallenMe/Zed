# Zed — native Android voice assistant

[![Build](https://github.com/ItzFallenMe/Zed/actions/workflows/build-apk.yml/badge.svg)](https://github.com/ItzFallenMe/Zed/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg)

Zed is a real-time, voice-to-voice assistant built as a native Android app in
Kotlin + Jetpack Compose. The microphone streams raw PCM to the Gemini Live
WebSocket API, and Gemini's native audio output is played through a persistent
`AudioTrack`. The conversation path has no browser, no `SpeechSynthesis`, no
pre-recorded audio, and no hard-coded reply.

The interface is a **Part 7-inspired liquid-glass shell**: four palettes, a
floating curved dock, grouped operator settings, and a live 3D particle orb.
The orb is an offline, network-disabled WebGL canvas rendered in a local
WebView; it is presentation only and never touches microphone audio, the Live
session, or device actions. Everything else is native Compose.

Created and maintained by **ItzFallenMe**.

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
  drive the orb, status pill, conversation header, and controls.
- **Complete assistant shell.** Home, Conversation, Settings, and Updates share
  the same stateful engine and are reachable from the floating dock.
- **Typed and spoken turns.** Typed messages use the same Gemini Live session as
  microphone input and completed turns remain available in the current session.
- **Speaker diagnostic.** Settings → *Speaker diagnostic* plays 440 Hz through
  the exact `AudioTrack` output graph used by Gemini audio.
- **Foreground session continuity.** An Android foreground service keeps an
  active voice session alive while the user temporarily switches apps.

## Design

The UI is built from a small, explicit design system instead of one-off styling:

- **Liquid glass.** Translucent gradient cards, hairline borders, specular
  highlights, and soft ambient glow fields.
- **Four palettes.** Pearl light (default), Obsidian dark, Ruby red, and Sunset
  glass. The selection is persisted and applied to both Compose and the orb.
- **Particle orb.** A Three.js volume with idle, listening, thinking, and
  speaking states. Zed's real microphone/playback level is pushed into the
  shader while a session is active.
- **Floating dock.** Five destinations with an elevated voice action and a
  curved light trail.
- **Grouped settings.** Operator identity, voice/model configuration, privacy,
  device actions, theme, and app identity each open a focused dialog.

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
app/src/main/
  assets/
    orb.html                   offline Three.js particle orb
    three.min.js               vendored renderer (MIT)
  java/com/zed/assistant/
    MainActivity.kt            activity, permission launcher, navigation
    ai/
      ZedEngine.kt             session state machine, tool routing
      GeminiLiveClient.kt      WebSocket protocol, setup, audio parsing, tools
      AudioEngine.kt           AudioRecord capture + AudioTrack playback queue
      AudioPcm.kt              MIME rate parsing, RMS, diagnostic tone
      DeviceActions.kt         validated allowlisted device actions
      ContactsRepository.kt    READ_CONTACTS name search
      PermissionGateway.kt     coroutine <-> runtime permission bridge
      ZedPersona.kt            Zed system instruction
    model/                     assistant state and completed turns
    ui/
      HomeScreen.kt            orb stage, live status, quick actions
      ConversationScreen.kt    typed and spoken conversation
      SettingsScreen.kt        grouped operator settings
      UpdatesScreen.kt         release notes and capabilities
      NavigationDock.kt        floating navigation and voice control
      ZedOrbView.kt            sandboxed local WebGL orb renderer
      components/              liquid-glass design system
      theme/                   palettes, typography, theme persistence
    viewmodel/MainViewModel.kt
    ZedSessionService.kt       foreground notification for active sessions
  res/                         icons, launch theme, backup rules
```

## Setup

1. Get a Gemini API key from Google AI Studio.
2. Either:
   - enter the key in **Zed → Settings → Gemini Live API & model**, or
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

Run the full local gate before opening a pull request:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The APK is produced by `.github/workflows/build-apk.yml` on pushes and pull
requests targeting `main` (artifact `zed-debug-apk`). The workflow also runs the
unit tests and Android lint.

## Permissions

Requested lazily, not all at startup:

- `RECORD_AUDIO` — when you first tap the mic.
- `READ_CONTACTS` — only when `callContact` actually needs to search contacts.
- `INTERNET` — Live WebSocket.
- `CALL_PHONE` — requested only at the moment you ask Zed to call someone.
  If granted, calls use `ACTION_CALL`; if denied, Zed opens the system dialer
  with the number (`ACTION_DIAL`) and reports that mode honestly.
- `MODIFY_AUDIO_SETTINGS` — communication audio mode and speaker routing.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, and
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — only while an active voice session is
  running, with a visible low-priority notification.

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

Audio, Live, and device actions require a physical device with a valid API key
and cannot be fully exercised in a build-only environment. The manual smoke
test is: configure the key, grant microphone permission, start a session, hear
Gemini voice, switch between spoken and typed turns, interrupt playback, test
the speaker, and verify one safe device action.

## Open-source project

Zed is MIT licensed and maintained by **ItzFallenMe**. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow,
[SECURITY.md](SECURITY.md) for responsible reports,
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community expectations,
[CHANGELOG.md](CHANGELOG.md) for release history, and
[LICENSE](LICENSE) for the reuse terms. Third-party asset notices are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
