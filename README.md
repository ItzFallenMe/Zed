# Zed — JARVIS for your Android phone

Zed is an open-source, voice-first assistant that actually **operates** your phone: it taps,
swipes, types, reads the screen, flips hardware switches, drives WhatsApp and guards the device
when it is left alone. Say **"Bolo Zed"** and talk in Hindi, English or Hinglish.

The assistant is built as a native Kotlin/Compose app because almost everything it does lives in
Android services: an accessibility engine, a foreground voice service, MediaProjection OCR and a
device-admin guard.

```
"Bolo Zed, torch jala do"                  → flashlight on
"Bolo Zed, WhatsApp par mom ko message     → opens the chat, types, sends
 bhejo ki main ghar aa raha hoon"
"Bolo Zed, screen padh kar sunao"          → reads the visible page aloud
"Bolo Zed, brightness 70"                  → sets brightness
"Bolo Zed, guard arm karo"                 → motion + charger siren guard
```

## Download

Grab the signed APK from the [Releases](../../releases) page. Every build is produced by the
GitHub Actions workflow in this repository.

- Minimum Android: **8.0 (API 26)**, targets **API 36**
- Sideload only — Zed is not on the Play Store (its permission set would need Play policy review)

## What it can do

### Device control (accessibility engine)
- Tap any visible label, swipe, long-press, scroll, type into focused fields
- Back / home / recent apps / lock screen / power menu / notification shade / quick settings
- Read the full UI tree, then act on it — this is what makes "control the phone" real

### Hardware controls
| Command | How it works |
|---|---|
| Flashlight | `CameraManager.setTorchMode` |
| Wi-Fi / Bluetooth | Toggles the real quick-settings tile through accessibility (Android 10+ blocks silent toggling for privacy) |
| Volume | `AudioManager`, absolute percentage or steps, plus mute/unmute |
| Brightness | `Settings.System` with `WRITE_SETTINGS` |
| Alarms / timers | `AlarmClock` intents — no battery-draining in-app scheduler |
| Battery | `BatteryManager`, including charging state |
| Launch any app | `PackageManager` resolution, falls back to web search |
| Media | Play/pause/next/previous media keys |

### Screen vision & OCR
- MediaProjection capture + ML Kit **on-device** OCR, Latin **and** Devanagari merged so Hinglish works
- Read the screen aloud, summarise, translate (on-device ML Kit translation), take a screenshot
- Find a button by text and tap it; auto-scroll feeds and shorts hands-free

### Voice
- **Wake word "Bolo Zed"** detected locally with the offline speech recognizer (configurable phrase,
  accepts "bolo jed", "hey zed", "अरे जेड", "jarvis")
- **Gemini Live** streaming conversation (PCM in/out over WebSocket) with **function calling**, so
  the model calls real device tools instead of pretending
- Local fallback: without an API key, wake word → on-device recognizer → offline parser → TTS
- Hindi/English/Hinglish, JARVIS-style personality ("sir"), JARVIS HUD interface

### WhatsApp automation
- Send text messages by contact name **or** number (opens chat, types, presses send)
- Send voice notes (long-press record gesture), search chats, start voice/video calls
- Read unread notifications (including WhatsApp) aloud through the notification listener

### Anti-theft siren guard
- Arms accelerometer motion detection plus charger-unplug detection
- On trigger: deafening two-tone siren (generated procedurally, no audio file), vibration,
  optional screen lock via device admin, and an intruder snapshot from the front camera
- Everything is stored locally in the app's private storage

## What is honestly limited by Android

No app can do these silently any more, and Zed tells you so instead of failing quietly:

- **Wi-Fi / Bluetooth** cannot be toggled programmatically from Android 10+. Zed drives the
  quick-settings tile; if the tile cannot be found it opens the system panel for one tap.
- **Wake word** runs a continuous offline recognizer, which costs battery. The phrase is only
  detected locally; audio is not uploaded for wake detection.
- **Gemini Live** sends microphone audio to Google *while a live conversation is open* — that is
  how the model hears you. Nothing is streamed in the background and no conversation is stored
  off-device by Zed. Without an API key, no audio ever leaves the phone.
- **WhatsApp** has no public send API, so Zed automates the UI. It can break when WhatsApp changes
  its layout; the chat stays open with your text if the final tap fails.
- **Screen vision** requires Android's screen-capture consent dialog each session, by design.
- Motion detection is sensor-based: very windy tables or haptic-heavy surfaces can trigger it. Use
  the sensitivity slider.

## Setup

1. Install the APK and open Zed.
2. Walk through the capability list:
   - **Microphone** — wake word and commands
   - **Notifications** — foreground service and notification reading
   - **Accessibility → Zed Device Control** — taps, swipes, typing, screen reading (the core of remote control)
   - **Notification access → Zed Notification Reader** — read unread messages aloud
   - **Modify system settings** — brightness
   - **Camera** — intruder snapshots
   - **Contacts / Phone** — "call mom"
   - **Device admin** — lock the screen during an intruder alert
3. Optional: add a Gemini API key in Settings for live conversation. Get one from
   [Google AI Studio](https://aistudio.google.com/apikey).
4. Toggle **Wake word** on and say **"Bolo Zed"**.

The Android 13+ quirk worth knowing: after a reboot, open Zed once. Android does not allow a
microphone foreground service to start itself from the background, so the wake word re-arms on
first launch.

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # parser + wake-word tests
./gradlew :app:assembleRelease      # signed with keystore/zed-release.jks
```

Requires JDK 17+ and Android SDK 36. CI does this automatically on every push.

## Architecture

```
MainActivity / Compose UI        ui/            HUD dashboard, setup, settings
ZedCoreService                   service/       wake word, live voice, command routing
ZedAccessibilityService          access/        taps, swipes, typing, screen text
ScreenCapture + OcrEngine        vision/        MediaProjection, ML Kit OCR, translation
GeminiLiveClient                 voice/         WebSocket audio + tool calls
IntentParser / CommandExecutor   command/       offline intents and tool execution
WhatsAppController               whatsapp/      chat automation
AntiTheftService + SirenPlayer   security/      motion guard, siren, snapshots
ZedNotificationListener          notify/        unread message reading
```

Commands flow: **wake word → speech → (offline parser | Gemini tool call) → CommandExecutor →
device layer → spoken confirmation**. The parser handles a fixed, fast set of commands; anything
else goes to Gemini with the tool schema attached.

## Privacy

- No accounts, no analytics, no telemetry, no third-party servers owned by this project.
- Conversation history and snapshots stay in the app's private storage.
- OCR, translation and wake-word detection run on device.
- The only network traffic is the Gemini Live WebSocket you explicitly enable with your own key.

See [PRIVACY.md](PRIVACY.md) for the detail.

## Signing key

`keystore/zed-release.jks` is committed **on purpose** so anyone can reproduce a byte-identical,
upgrade-compatible APK in CI. Treat it as a community demo key: it is not suitable for Play Store
distribution. Rotate it (and set `ZED_STORE_PASSWORD` / `ZED_KEY_PASSWORD` secrets) if you fork.

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE). Do what you like, keep the notice, ship it responsibly.
