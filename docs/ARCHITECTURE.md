# Architecture

Zed has one owner for conversation state and separate, narrow adapters for
audio, transport, device actions, and presentation.

```
MainActivity
  └─ MainViewModel ──────────────┐
                                 │ StateFlow
ZedEngine (application scoped) ◄─┘
  ├─ PermissionGateway  → runtime permission requests
  ├─ GeminiLiveClient   → BidiGenerateContent WebSocket
  ├─ AudioEngine        → AudioRecord capture + AudioTrack queue
  ├─ DeviceActions      → allowlisted intents + contact lookup
  ├─ ZedPersona         → system instruction
  └─ ZedSessionService  → foreground notification while active

Compose UI
  ├─ HomeScreen / ConversationScreen / SettingsScreen / UpdatesScreen
  ├─ ZedOrbView         → local, network-disabled WebGL presentation
  └─ theme/             → palettes + persisted appearance mode
```

## State ownership

`ZedEngine` is an application-scoped object because the Live session must
survive activity recreation and short app switches. It exposes read-only
`StateFlow`s for:

- `state` — `IDLE`, `CONNECTING`, `LISTENING`, `SPEAKING`, `ERROR`
- `status` — human-readable session line
- `userText`, `zedText` — the in-flight exchange
- `chatTurns` — completed user/assistant turns
- `errorMessage`, `actionNote`, `isActive`, `level`

The UI never opens its own WebSocket, microphone, or audio track. This keeps
barge-in, interruption, reconnection, and foreground-service behavior in one
place.

## Live session lifecycle

1. A tap asks `PermissionGateway` for `RECORD_AUDIO`.
2. `ZedEngine` starts `ZedSessionService` and reads the API key from private
   preferences or `BuildConfig`.
3. `GeminiLiveClient` opens the Live WebSocket and sends setup with the model,
   voice, system instruction, and tool declarations.
4. `AudioEngine` streams 16 kHz PCM16 microphone chunks as `realtimeInput`.
5. Ordered 24 kHz PCM16 chunks from every `modelTurn.parts[]` entry are queued
   into one persistent `AudioTrack`.
6. `serverContent.interrupted` or a long-press flushes playback and returns to
   listening.
7. An unexpected close attempts resumption with the last handle; otherwise the
   error state exposes a retry path.

Typed messages use the same session. They are queued and dispatched only after
setup completes and the previous turn is finished, so a message is never
silently dropped while the socket is still connecting.

## Tool boundary

`DeviceActions` accepts only the tools declared in the Live setup:

- `openApp` — allowlisted package names only
- `openWhatsApp` — installed WhatsApp package required
- `openUrl` — `https` only
- `makeCall` / `callContact` — validated numbers and ambiguous-contact results

Every tool returns a structured JSON result. The persona instruction requires
Zed to report the result honestly rather than claiming an action succeeded.

## Presentation boundary

The UI reads state and calls intent methods on `ZedEngine` through
`MainViewModel`. It does not hold an Activity context in the engine. The orb is
a local `file:///android_asset/orb.html` page inside a WebView with network
loading disabled; it is not part of the audio or conversation path.

Theme selection is a separate `ZedThemeRepository` state flow persisted in the
same private preferences file. Changing a palette recomposes Compose and sends
a theme key to the orb.

## Privacy boundaries

- The API key is stored in app-private `SharedPreferences` and Android backup is
  disabled.
- Base64 audio is never logged.
- Contacts are read only for an explicit `callContact` request.
- No accessibility, overlay, SMS, or phone-state permissions are requested.
- The foreground notification is visible while a session runs.
