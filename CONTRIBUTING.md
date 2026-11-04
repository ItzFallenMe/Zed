# Contributing to Z.E.D.

Thanks for helping improve Z.E.D. Keep changes focused, explain user-visible
behaviour, and preserve the safety boundaries around microphone access, creator
verification, device actions, and API-key handling.

## Development setup

1. Install JDK 17 and Android SDK platform 36/build-tools 36.
2. Open the repository in Android Studio or use the included Gradle wrapper.
3. Configure a Gemini API key only through local Gradle properties, an
   environment variable, or the in-app Settings screen. Never commit keys.
4. Run the checks below before opening a pull request.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:lintDebug
```

A physical Android phone is required to validate microphone capture, Gemini
audio playback, wake word, overlay, accessibility, calls, and torch. Emulator
results are not enough for those paths.

Release verification is documented in [docs/RELEASING.md](docs/RELEASING.md).

## Code layout

- `app/src/main/java/com/zed/assistant/ai/` owns the Live transport, audio
  engine, permission bridge, persona, and device actions. UI code must not open
  its own socket, microphone, or audio track.
- `app/src/main/java/com/zed/assistant/audio/` owns the Vosk wake detector,
  speaker verifier, and local creator challenge.
- `app/src/main/java/com/zed/assistant/data/` owns preferences, creator auth,
  memory, chat history, weather, and voice profiles.
- `app/src/main/java/com/zed/assistant/automation/` owns the accessibility
  service and screen tools.
- `app/src/main/java/com/zed/assistant/overlay/` owns the floating robot.
- `app/src/main/java/com/zed/assistant/ui/` is the Compose presentation layer.
  Read the [design system](docs/DESIGN.md) before adding a screen or colour.
- `app/src/main/java/com/zed/assistant/ui/theme/` owns palettes, Material 3
  mapping, and persisted appearance state.
- `app/src/main/assets/robot-head/` is the offline robot-head page. Keep network
  access disabled and do not put conversation data in the WebView.
- Architecture and state ownership are documented in
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Design rules

- Material 3 is the default; liquid glass must remain opt-in through
  `LocalLiquidGlassEnabled`.
- Use tokens from `LocalZedPalette` and `MaterialTheme`; do not hard-code brand
  colours in screens.
- Keep the robot-head avatar state-driven and presentation-only.
- Preserve the black-first identity. Obsidian is the default palette and the
  fallback for unknown stored themes.
- Every icon-only button needs a content description. Touch targets stay at or
  above 44 dp.

## Safety and privacy rules

- Never send the creator code, creator speech transcript, wake audio, or speaker
  vectors to Gemini or any remote service.
- New tools must validate arguments, return a structured result, and report
  failure honestly. Do not claim an action succeeded without a result.
- Do not add SMS, call-log, or phone-state access without a documented product
  requirement and review.
- Do not broaden accessibility, overlay, location, contacts, or package
  visibility without documenting the user-facing reason.
- Do not log Base64 audio, API keys, phone numbers, contacts, or creator codes.
- Keep Android backup disabled.

## Tests

Add unit tests for pure logic where practical: phrase matching, spoken-digit
normalisation, PBKDF2 verification, theme fallback, MIME parsing, URL
validation, and tool argument validation.

```bash
./gradlew :app:testDebugUnitTest
```

For audio, Live, accessibility, overlay, and device-action changes, include
manual device-test notes in the pull request.

## Pull requests

- Keep each pull request focused on one feature or fix.
- Add or update documentation when behaviour or permissions change.
- Update `CHANGELOG.md` for user-visible changes.
- Use the repository's Kotlin formatting, 2-space indentation, and package
  structure.
- Explain any new permission or creator-control path in the PR description.
- Include screenshots only when they help explain a visual change; do not
  include personal data, keys, phone numbers, or notification content.

## Commit guidance

Use imperative, descriptive commit subjects such as `fix: handle Live
reconnection` or `feat: add voice preset picker`. Avoid committing generated
APKs, local properties, keystores, API keys, or device logs.

## Reporting issues

Use the issue templates and redact API keys, phone numbers, contacts, audio, and
device logs before posting. Security reports should follow
[SECURITY.md](SECURITY.md) instead of opening a public issue.
