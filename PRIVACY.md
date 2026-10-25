# Privacy

Zed is built so the private parts stay local.

## What stays on the phone

- **Wake word audio.** Detection runs through the Android on-device speech recognizer with the
  offline preference enabled. No audio is uploaded for wake detection.
- **Voiceprints.** A 48-number mel-spectrum fingerprint per enrolled person, computed on device
  and stored as JSON in private app storage. The audio used to create it is discarded immediately.
- **Conversation history.** Kept in memory for the session and, if "Keep chat history" is on, in
  private app storage. Never written to an external database.
- **Screen content.** OCR and translation run through ML Kit on device. Frames streamed to the
  live model exist only for the duration of that conversation and are never stored by Zed.
  Screenshots are written to private storage only when you ask for one.
- **Intruder snapshots.** Private app storage only.
- **Settings and API keys.** Private `SharedPreferences` for this app.
- **Notification content.** A capped in-memory buffer used to read messages aloud.
- **Memory.** Facts you ask Zed to remember, plus summaries of finished conversations, are
  stored as JSON in private app storage with their embeddings, and can be wiped from Settings.
  The identity code that marks the creator is verified locally and never sent anywhere.
- **Voiceprints and creator status** are local preferences, not account data.

## What touches the network

Two providers, each with a key you supply, and only while you are using the feature:

- **Gemini** (`generativelanguage.googleapis.com`) — realtime voice and vision. While a
  conversation is open, microphone audio is streamed to Google, and if screen awareness is on, a
  low-resolution frame roughly every second while you talk. Closing the conversation stops both.
- **Groq** (`api.groq.com`) — text reasoning for typed messages and multi-step actions, and the
  conversation summaries that become long-term memory. Sends the recent conversation text and
  tool results.
- **Gemini embeddings** (`text-embedding-004`) — turns memory items and search queries into
  vectors so recall is semantic. Sends only the memory text or the query, never audio.

Turn off "Cloud reasoning" and Zed never connects at all: every offline hardware command still
works through the on-device parser.

Zed itself has no backend, no accounts, no analytics and no crash reporting.

## Permissions and why

| Permission | Reason |
|---|---|
| Microphone | wake word and voice commands |
| Accessibility | taps, swipes, typing, screen reading — the core of phone control |
| Notification access | read unread messages aloud |
| Display over other apps | the floating card that unfolds on "zed" |
| Modify system settings | brightness |
| Camera | anti-theft snapshots |
| Contacts, phone | call a contact by name |
| Screen capture | seeing the screen, one consent per session |
| Query installed apps | launch apps by name |

Zed does **not** request device admin, SMS or install-packages permissions.

Revoke any permission at any time in Android settings; the matching feature stops working and the
rest keeps going.

## Deleting your data

Android Settings → Apps → Zed → Storage → Clear data removes every preference, chat, voiceprint
and snapshot.
