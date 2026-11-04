# Security policy

## Supported versions

Security fixes are applied to the latest commit on `main` and to the latest
tagged release. Z.E.D. 2.0.0 is the current supported release line.

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Contact the
maintainer privately through the email address listed on the repository owner
profile and include:

- a clear description of the issue and its impact;
- the affected version, commit, screen, or Android version;
- reproducible steps or a minimal proof of concept; and
- any suggested mitigation.

Do not include real API keys, phone numbers, contact data, creator codes, or
private audio in a report. Remove secrets before sharing logs.

## Secret handling

Z.E.D. stores a user-entered Gemini key and a creator-code verifier in private
app preferences. Contributors must never place a key, creator code, or keystore
in source, Gradle files, screenshots, commits, CI logs, or issue comments.

- The Gemini key is sent only to Google's Live endpoint when a session starts.
- Public distributions should use ephemeral Live tokens or a backend proxy
  instead of shipping a long-lived API key.
- Android backup is disabled for the app's private data.

## Creator protocol

- The first-run creator code is `160123`. It is a bootstrap secret and should
  be changed immediately after the first successful unlock.
- The code is stored as a PBKDF2-HMAC-SHA256 hash (120,000 iterations, random
  salt) and sealed with an Android Keystore key through `SecureStore`.
- The code and the speech-recognition transcript never pass through Gemini.
- Three failed attempts trigger a persisted 60-second lockout.
- Creator access remains unlocked until the creator explicitly locks it.

### Threat boundaries

The creator gate is designed to prevent accidental access and casual guessing
on an unlocked phone. It is **not** a defence against:

- a rooted device, a compromised OS, or a malicious app with equivalent
  privileges;
- an attacker who can read the user's unlocked screen;
- physical coercion; or
- a modified build that removes the gate.

The 60-second lockout is a speed bump, not a rate-limit service. There is no
remote attestation and no server-side verification.

## Permissions with elevated impact

- `SYSTEM_ALERT_WINDOW` allows the floating robot overlay.
- Accessibility access allows screen reading and controlled taps. It is used
  only for explicit user requests.
- `QUERY_ALL_PACKAGES` allows finding launchable apps by name. It is broad; a
  distribution that does not need it should narrow the package visibility.
- `WRITE_SETTINGS` allows brightness changes.
- `READ_CONTACTS` and `CALL_PHONE` are used only for explicit call requests.
- Location is used only for an explicit weather request.

Report any path where one of these can be triggered without a user request or a
structured tool result.
