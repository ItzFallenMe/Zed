# Zed — a phone assistant with a personality

Zed is an open-source, voice-first assistant that actually **operates** your phone: it taps,
swipes, types, reads your screen, flips hardware switches, drives WhatsApp and keeps watch
over the device when you leave it alone. Say **"Zed"** — once — and talk.

It is not a command list with a voice bolted on. Zed has a name, a manner and a memory, and it
talks like a person on the other end of a call: short sentences, opinions, the occasional joke.
Under that it has real tools, real eyes and real hands on the device.

Built by [ItzFallenMe](https://github.com/ItzFallenMe).

```
"Zed, it's dark in here"                      → torch on
"Zed, message mom on WhatsApp I'll be late"   → opens the chat, types, sends
"Zed, what am I looking at?"                  → reads the screen and answers
"Zed, this song is boring"                    → skips it
"Zed, I parked in B2, remember that"          → stored in local memory
"Zed, guard the phone"                        → motion + charger siren guard
```

## Download

Grab the signed APK from the [Releases](../../releases) page. Every build is produced by the
GitHub Actions workflow in this repository.

- Minimum Android: **8.0 (API 26)**, targets **API 36**
- Sideload only — Zed is not on the Play Store
- Installing it over Play Protect is explained in [INSTALL.md](INSTALL.md)

## First run

Zed asks for two free keys before anything else, because both halves of its brain need one:

| Key | What it powers | Where to get it |
|---|---|---|
| **Gemini** | realtime voice, hearing and seeing | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |
| **Groq** | fast replies, multi-step actions when you type | [console.groq.com/keys](https://console.groq.com/keys) |

Both have free tiers, both keys stay in the app's private storage, and neither is ever sent
anywhere except to its own provider. Then grant the capabilities you actually want.

## The interaction model

- **Say "Zed"** from anywhere. An on-device recogniser listens constantly for the name only;
  that audio never leaves the phone.
- A **glass card unfolds** over whatever app you are in, showing live captions, and folds away
  when you are done. There is no app to open and no screen to navigate.
- Talk naturally, or type. Spoken input goes to Gemini Live (streaming audio both ways, with
  tool calls). Typed input goes to Groq, which can chain several phone actions in one go.
- **One consent** for screen capture, then Zed sees what you see for the rest of the session —
  no "share this app" picker, no repeated dialogs.
- Zed **knows your voice** once you teach it: four seconds of speech is enough to greet you by
  name and treat you as you.

## What it can do

### Device control (accessibility engine)
- Tap any visible label, swipe in any direction, long-press, scroll, type into focused fields
- Back / home / recent apps / lock screen / power menu / notification shade / quick settings
- Toggle any quick-settings tile (airplane mode, hotspot, DND, dark theme, mobile data …)
- Read the whole UI tree and act on it — this is what "control the phone" means in practice

### Hardware
| Command | How it works |
|---|---|
| Flashlight | `CameraManager.setTorchMode` |
| Wi-Fi / Bluetooth | Drives the real quick-settings tile (Android 10+ blocks silent toggling) |
| Volume | `AudioManager`, absolute percentage or steps, plus mute/unmute |
| Brightness | `Settings.System` with `WRITE_SETTINGS` |
| Alarms / timers | `AlarmClock` intents — no battery-draining in-app scheduler |
| Battery / time | `BatteryManager` and the system clock |
| Launch any app | `PackageManager` resolution, falls back to web search |
| Media | Play/pause/next/previous media keys |

### Eyes on the screen
- MediaProjection capture with ML Kit **on-device** OCR, Latin **and** Devanagari, so Hinglish works
- **Hands-free streaming**: once you consent, frames flow to the live conversation while you
  talk, so "what's this?" just works
- Read aloud, summarise, translate (on-device ML Kit), screenshot, find a button by text and tap
  it, auto-scroll feeds and shorts

### Voice
- **Wake word "zed"**, matched against the spellings recognisers actually produce (`zad`, `zade`,
  `जेड`, "hey zed"), fully on device
- **Gemini Live** conversation: PCM in and out over WebSocket with function calling
- **Best available voice**: Google speech services, highest-quality installed voice for the
  language, network voices when online, best offline voice otherwise
- **Choose the voice**: any installed system voice, or a Gemini Live voice
- **Voice identity**: an on-device mel-spectrum voiceprint tells Zed who is talking, from the
  people you enrolled. Stored locally, never uploaded

### Memory and personality
- Durable local memory: "remember my parking spot is B2" survives restarts
- Custom instructions you write yourself, appended to every system prompt
- Status awareness: time, battery, unread notifications, current app, vision and guard state
- Zed speaks Hindi, English or Hinglish, mirroring whatever mix you use

### WhatsApp
- Send text by contact name **or** number (opens the chat, types, presses send)
- Send voice notes, search chats, start voice or video calls
- Read unread notifications aloud through the notification listener

### Anti-theft guard
- Accelerometer motion plus charger-unplug detection
- On trigger: procedurally generated two-tone siren, vibration, optional screen lock through the
  accessibility service, and an intruder snapshot from the front camera
- Everything stays in the app's private storage

## Free only

No paid service anywhere. Gemini Live runs on the free Gemini tier with your own key, Groq on its
free tier, and OCR, translation, voiceprints, wake word and speech synthesis run on-device with
free Android and ML Kit components.

## What is honestly limited by Android

- **Wi-Fi / Bluetooth** cannot be toggled silently since Android 10. Zed drives the quick-settings
  tile, and opens the panel for one tap if the tile cannot be found.
- **Wake word** runs a continuous offline recogniser, which costs some battery. Only the name is
  detected locally; no audio is uploaded for wake detection.
- **Screen capture** needs Android's consent dialog once per capture session. Zed asks once and
  then keeps the session alive, including while you are in other apps.
- **Gemini Live** sends microphone audio to Google *while a conversation is open*. Nothing is
  streamed in the background.
- **WhatsApp** has no public send API, so Zed automates the UI. A WhatsApp redesign can break it;
  the chat stays open with your text if the final tap fails.
- **Voice recognition** is a lightweight on-device fingerprint, not a biometric lock. It is good
  at telling enrolled housemates apart; it is not a security boundary.

## Play Protect and installation

Android may show **"Unsafe app blocked"** for Zed. This is the standard reaction to a sideloaded
app that asks for accessibility and notification access — the same combination any remote-control
assistant needs — not evidence of anything wrong with the APK.

Zed already does what it can from the inside to stay quiet: **no device admin** (screen lock goes
through accessibility instead), no SMS permissions, no install-packages permission, and no
background screen recording. What remains is Play Protect's judgement about the permission
combination itself, which no app can code around.

[INSTALL.md](INSTALL.md) walks through the three ways past it, in order of preference:
build it yourself, install with `adb`, or tap through the "Install anyway" / temporarily disable
Play Protect's scanner.

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # parser, wake word, voiceprint tests
./gradlew :app:assembleRelease      # signed with keystore/zed-release.jks
```

Requires JDK 17+ and Android SDK 36. CI does this on every push and publishes the APK.

## Architecture

```
MainActivity / Compose UI     ui/         glass chat, keys onboarding, setup, settings, floating card
FloatingAssistant             ui/         the overlay that unfolds on wake
ZedCoreService                service/    wake word, live voice, routing, screen streaming
ZedAccessibilityService       access/     taps, swipes, typing, screen text
ScreenCapture / ScreenStreamer vision/    MediaProjection and hands-free frame streaming
OcrEngine / OnDeviceTranslator vision/    ML Kit text recognition and translation
GeminiLiveClient              voice/      WebSocket audio, video frames, tool calls
GroqBrain                     command/    OpenAI-style function calling for typed chat
IntentParser / CommandExecutor command/   offline intents and tool execution
Voiceprint / VoiceProfileStore voice/     on-device speaker recognition
WhatsAppController            whatsapp/   chat automation
AntiTheftService + SirenPlayer security/  motion guard, siren, snapshots
ZedNotificationListener       notify/     unread message reading
MemoryStore                   core/       local long-term facts
```

Flow: **"zed" → speech → (offline parser | Gemini Live tool call | Groq tool call) →
CommandExecutor → device layer → spoken reply**, with the overlay reflecting every state.

## Privacy

- No accounts, no analytics, no telemetry, no servers owned by this project.
- Conversations, screenshots, memories and voiceprints stay in app-private storage.
- OCR, translation, voiceprints and wake-word detection run on device.
- The only network traffic is Gemini and Groq, with your own keys, while you are talking.

See [PRIVACY.md](PRIVACY.md) for detail.

## Signing key

`keystore/zed-release.jks` is committed **on purpose** so anyone can reproduce an
upgrade-compatible APK in CI. Treat it as a community demo key, not a Play Store key. Rotate it
(and set `ZED_STORE_PASSWORD` / `ZED_KEY_PASSWORD` secrets) if you fork.

## Design

Pure-black canvas, white accents, frosted glass. Material 3 throughout, animated squircle avatar,
drifting light pools behind the glass, and no borrowed sci-fi interface.

## Creator

Built by **ItzFallenMe** — [github.com/ItzFallenMe](https://github.com/ItzFallenMe).

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE). Do what you like, keep the notice, ship it responsibly.
