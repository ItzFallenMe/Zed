# Privacy

Zed is built so that the private parts stay local.

## What stays on the phone

- **Wake word audio.** Detection runs through the Android on-device speech recognizer with
  offline preference enabled. It is not uploaded for wake detection.
- **Conversation history.** Kept in memory for the session and in the app's private storage if
  "Keep local chat history" is on. Never written to an external database.
- **Screen content and OCR text.** Processed in memory by ML Kit on device. Screenshots are saved
  only when you ask for one, into the app's private files directory.
- **Intruder snapshots.** Private app storage only.
- **Settings and API key.** Private `SharedPreferences` for this app.
- **Notification content.** A capped in-memory buffer used to read messages aloud.
- **Memory.** Facts you ask Zed to remember are stored as JSON in private app storage and can be
  wiped from Settings → Memory.

## What touches the network

Only one thing, and only when you enable it:

- **Gemini Live** (`wss://generativelanguage.googleapis.com`) with a key you supply. While a live
  conversation is open, microphone audio is streamed to Google so the model can respond. Turn off
  "Live conversation" or "Allow cloud reasoning" and Zed never connects at all — every hardware
  command still works through the offline parser.

Zed itself has no backend, no accounts, no analytics and no crash reporting.

## Permissions and why

| Permission | Reason |
|---|---|
| Microphone | wake word and voice commands |
| Accessibility | taps, swipes, typing, screen reading — the core of phone control |
| Notification access | read unread messages aloud |
| Modify system settings | brightness |
| Camera | anti-theft snapshots |
| Contacts, phone | call a contact by name |
| Device admin | lock the screen on an intruder alert |
| Query installed apps | launch apps by name |

Revoke any of them at any time in Android settings; the matching feature stops working and the
rest keeps going.

## Deleting your data

Android Settings → Apps → Zed → Storage → Clear data removes every preference, chat and snapshot.
