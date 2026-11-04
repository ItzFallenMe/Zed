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
  ├─ HeadsetAudioRouter        communication-device selection
  ├─ DeviceActions              intents, contacts, media, system controls
  ├─ ZedAccessibilityService    screen tree, clicks, text, scroll, lock
  ├─ AgentTaskEngine            bounded ordered multi-step actions
  ├─ ScreenCaptureService       MediaProjection JPEG frames
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
| Apps | `openApp`, `openWhatsApp`, `openUrl`, `searchYouTube`, `playYouTube` |
| Communication | `makeCall`, `callContact`, `sendWhatsAppMessage` with optional `sendNow` |
| Media and system | `setVolume`, `controlMedia`, `setBrightness`, `setFlashlight` |
| Information | `getTime`, `getWeather` |
| Screen | `inspectScreen`, `clickElement`, `typeText`, `scrollScreen`, `pressKey` |
| Multi-step | `runTask` → `AgentTaskEngine` |
| Creator only | `creatorControl` |

`DeviceActions` validates arguments and never guesses an ambiguous contact,
package, or number. If `CALL_PHONE` is denied, it opens the dialer and reports
`mode: "dialer"`. WhatsApp handoff opens a prefilled compose screen; with
`sendNow` and accessibility it taps Send and reports `auto_send_tapped`. Z.E.D.
never claims delivery. YouTube auto-play follows the same rule: a tapped result
is `auto_play_tapped`; otherwise the result is a results-page fallback.

`ZedAccessibilityService` is a normal bound accessibility service. It is used
only for an explicit screen/action request and never automates the device in
the background. `pressKey("lock_screen")` is additionally gated by creator
access. The accessibility helpers also require the service to be enabled;
every caller receives a structured `accessibility_service_disabled` result
rather than a silent no-op.

### Multi-step tasks

`runTask` accepts a goal and at most 12 declared steps. `AgentTaskEngine` runs
them sequentially through the same `DeviceActions` and accessibility helpers as
single tool calls. Each step has a 5-second maximum wait and an optional
`expect` string that is checked against the accessibility tree. The engine
stops at the first failure, returns the completed step count and failed step,
and never retries a partially applied action automatically. Progress is written
to `ZedEngine.status`, which both the conversation UI and overlay observe.
Long-press on the overlay cancels at the next safe boundary.

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
7. `AppIntegrity` optionally pins the signing certificate. When
   `ZED_SIGNING_CERT_SHA256` is configured, a mismatch blocks master control
   with `app_signature_mismatch`. An unpinned build is transparent about not
   being pinned.

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
`WakeWordDetector` keeps a rolling six-second PCM pre-roll; when the profile is
enabled, `ZedWakeWordService` verifies that pre-roll before opening a session
and shows a "Voice not recognized" notification on a mismatch. The profile is a
local similarity check, not biometric authentication, and does not replace the
creator code.

`AudioEngine` creates a `HeadsetAudioRouter` for each session. The router picks
a Bluetooth/wired/USB/BLE communication device (or SCO on older APIs), falls back
to the loudspeaker only when no headset exists, and restores the previous mode
and speaker state when the session ends.

## Screen context

Live screen context has two paths:

- `ScreenCaptureService` is a `mediaProjection` foreground service. Settings
  launches the system consent dialog, then starts the service with the returned
  grant. Every capture shows an ongoing notification with a Stop action. The
  service downsamples to a 640-pixel edge and sends a JPEG about every two
  seconds through `ZedEngine.sendScreenFrame`.
- If projection is unavailable, `ZedEngine` falls back to accessibility
  screenshots at the same cadence. The fallback stops automatically when the
  projection service starts.

Either path stops when the conversation tears down, when the user taps Stop, or
when Android revokes projection.

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
- Screen frames are captured only with active MediaProjection consent (or the
  accessibility fallback the user enabled) and only while a session is active.
- No analytics, advertising, telemetry, or crash-reporting SDK is included.
- Foreground notifications are visible while the microphone or screen capture
  is active.

## Failure behaviour

The app prefers a truthful failure over a confident lie:

- missing WhatsApp → `whatsapp_not_installed`
- ambiguous contact → candidate list, no call
- denied phone permission → dialer fallback with `mode: "dialer"`
- unavailable accessibility → explicit `accessibility_service_disabled`
- denied overlay permission → overlay remains off and Settings explains why
- failed task step → completed/failed step counts, no automatic retry
- screen projection revoked → foreground service stops and the fallback resumes
- signing pin mismatch → `app_signature_mismatch`, no master control
- dropped Live socket → resume or expose retry; never claim the session is live
