# Architecture

Z.E.D. keeps one owner for conversation state and narrow adapters for audio,
transport, device actions, local identity, and presentation. The app is a single
Android module; there is no backend service and no remote account system.

```text
MainActivity
  └─ MainViewModel ──────────────────────────────┐
                                                   │ StateFlow
ZedEngine (application scoped) ◄───────────────────┘
  ├─ PermissionGateway          runtime permission bridge
  ├─ GeminiLiveClient           Live WebSocket, setup, tool dispatch
  ├─ AudioEngine                AudioRecord capture + AudioTrack queue
  ├─ DeviceActions              intents, contacts, media, system controls
  ├─ ZedAccessibilityService    screen tree, clicks, text, scroll, lock
  ├─ CreatorAuth                code hash, unlock state, lockout
  ├─ CreatorChallengeSession    TTS prompt + one-shot SpeechRecognizer
  ├─ ZedPersona                 identity, tone, safety boundaries
  ├─ VoiceProfiles              preset → native voice + delivery style
  ├─ ChatHistoryStore           local SQLite transcript
  ├─ MemoryStore                local durable facts
  ├─ WeatherRepository          location + Open-Meteo
  └─ ZedSessionService          foreground notification while active
        ▲
        │ application context
ZedWakeWordService ── VoskModelManager ── WakeWordDetector
ZedOverlayService  ── robot-head WebView
```

## State ownership

`ZedEngine` is application-scoped because a Live session must survive activity
recreation and short app switches. It exposes read-only `StateFlow`s:

- `state` — `IDLE`, `CONNECTING`, `LISTENING`, `SPEAKING`, `ERROR`
- `status` — human-readable session line
- `userText`, `zedText` — in-flight exchange
- `chatTurns` — completed user/assistant turns
- `errorMessage`, `actionNote`, `isActive`, `level`
- creator state: `creatorUnlocked`, `creatorPrompt`

The UI never opens its own WebSocket, microphone, or audio track. Barge-in,
interruption, reconnect, creator verification, and foreground-service behaviour
stay in one place.

`MainViewModel` reads those flows and owns screen-level preferences (theme,
liquid glass, voice preset, wake/overlay toggles). It delegates all conversation
and creator intents to the engine.

## Live session lifecycle

1. A tap or wake event asks `PermissionGateway` for `RECORD_AUDIO`.
2. `ZedEngine` starts `ZedSessionService` and reads the API key from private
   preferences or `BuildConfig`.
3. `GeminiLiveClient` opens the Live WebSocket and sends setup: model, native
   voice, system instruction, and tool declarations.
4. `AudioEngine` streams 16 kHz PCM16 microphone chunks as `realtimeInput`.
5. Ordered 24 kHz PCM16 chunks from every `serverContent.modelTurn.parts[]`
   entry are queued into one persistent `AudioTrack`.
6. `serverContent.interrupted` or a long-press flush stops playback and returns
   to listening.
7. An unexpected close attempts resumption with the last handle; otherwise the
   error state exposes a retry path.

Typed messages use the same session. They are queued until setup completes and
the current turn finishes, so a message is not silently dropped while the socket
is connecting.

## Tool boundary

Tools are declared in the Live setup and dispatched by `ZedEngine`. Every tool
returns a structured JSON result; the persona instruction requires Z.E.D. to
report the result honestly.

| Area | Examples |
| --- | --- |
| Apps | `openApp`, `openWhatsApp`, `openUrl`, `searchYouTube` |
| Communication | `makeCall`, `callContact` |
| Media and system | `setVolume`, `mediaControl`, `setBrightness`, `setFlashlight` |
| Information | `getTime`, `getWeather` |
| Screen | `inspectScreen`, `clickText`, `typeText`, `scroll`, `pressKey` |
| Creator only | `creatorControl` |

`DeviceActions` validates arguments and never guesses an ambiguous contact,
package, or number. If `CALL_PHONE` is denied, it opens the dialer and reports
`mode: "dialer"`. WhatsApp handoff opens a prefilled compose screen; Z.E.D. does
not claim the message was sent.

`ZedAccessibilityService` is a normal bound accessibility service. It is used
only for an explicit screen request and never automates the device in the
background. `pressKey("lock_screen")` is additionally gated by creator access.

## Creator gate

Creator verification is deliberately local:

1. `CreatorGate` matches spoken/typed creator phrases and normalises spoken
   digits.
2. `CreatorAuth` verifies the six-digit code against a PBKDF2-HMAC-SHA256 hash
   (120,000 iterations, random salt) stored in private preferences.
3. The stored verifier is encrypted by `SecureStore` using an Android Keystore
   key.
4. Three failures set a persisted `lock_until` timestamp 60 seconds in the
   future.
5. `CreatorChallengeSession` uses Android `TextToSpeech` and a one-shot
   `SpeechRecognizer` for the spoken path. The transcript stays in the app and
   is never forwarded to Gemini.
6. On success, `creatorUnlocked` is persisted and `creatorControl` becomes
   available. The creator can change the code or lock access at any time.

The first-run code is `160123`. It is a bootstrap secret, not a remote
credential. The gate is designed to stop accidental access and casual guessing
on an unlocked phone; it is not a defence against a rooted device or a
physical attacker.

## Voice profiles

`ZedVoiceProfiles` is a pure data table. Each entry maps a stable preset id to a
Gemini native voice name, a human description, and a delivery instruction. At
session start, `ZedEngine` resolves the saved preset and passes both the voice
name and the style direction into `GeminiLiveClient`/`ZedPersona`.

The preset selection is separate from the model selection, so changing a voice
does not require a new API key or a new transport.

## Presence and wake word

`ZedWakeWordService` is a microphone foreground service. It owns the Vosk model,
feeds 16 kHz PCM into `WakeWordDetector`, and starts a conversation when the
wake phrase is recognised. The detection path is offline; the only network
traffic is the Gemini session that follows.

`ZedOverlayService` draws a small WebView robot using
`SYSTEM_ALERT_WINDOW`. It does not hold a microphone. The overlay and wake
service are synchronised from `MainActivity.onResume()` so returning from a
system permission screen immediately reflects the saved preference.

Owner-voice enrolment records three short samples, extracts x-vector embeddings
with the bundled speaker model, and stores the vectors in private preferences.
The profile is used for local speaker matching and does not replace the creator
code.

## Presentation boundary

The Compose layer reads state and calls intent methods on `MainViewModel`.

- `HomeScreen` presents the robot-head stage and primary voice action.
- `ConversationScreen` renders live and persisted turns and a composer.
- `SettingsScreen` groups identity, voice, presence, creator, memory,
  permissions, and appearance.
- `UpdatesScreen` is static release information.
- `CreatorAccessDialog` is hosted above navigation so the challenge cannot be
  lost when a screen changes.
- `LiquidGlass.kt` switches all shared surfaces between liquid glass and solid
  Material 3 using `LocalLiquidGlassEnabled`.

Theme and liquid-glass choices are persisted in the same private preferences
file. Changing either recomposes the UI without touching the engine.

## Privacy boundaries

- API key and creator verifier are app-private; Android backup is disabled.
- Creator codes and speech-recognition transcripts never go to Gemini.
- Wake-word audio and speaker vectors stay on-device.
- Base64 audio is never logged.
- Contacts are read only for an explicit contact-call request.
- Location is requested only for a weather request.
- No analytics, advertising, telemetry, or crash-reporting SDK is included.
- The foreground notification is visible while a microphone session runs.

## Failure behaviour

The app prefers a truthful failure over a confident lie:

- missing WhatsApp → `whatsapp_not_installed`
- ambiguous contact → candidate list, no call
- denied phone permission → dialer fallback with `mode: "dialer"`
- unavailable accessibility → explicit `accessibility_not_enabled`
- denied overlay permission → overlay remains off and Settings explains why
- dropped Live socket → resume or expose retry; never claim the session is live
