# Contributing to Zed

Thanks for helping improve Zed. Keep changes focused, explain user-visible
behavior, and preserve the safety boundaries around microphone access, device
actions, and API-key handling.

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

Release verification is documented in [docs/RELEASING.md](docs/RELEASING.md).

## Code layout

- `app/src/main/java/com/zed/assistant/ai/` owns audio, Live transport,
  permissions, persona, and device actions. UI code must not open its own
  socket, microphone, or audio track.
- `app/src/main/java/com/zed/assistant/ui/` is the presentation layer. Read the
  [design system](docs/DESIGN.md) before adding a screen or a new colour.
- `app/src/main/java/com/zed/assistant/ui/theme/` owns palettes and persisted
  appearance state. Components should read `LocalZedPalette` rather than
  hard-coding colours.
- `app/src/main/assets/orb.html` and `three.min.js` are the offline orb. Keep
  network access disabled and do not put conversation data in the WebView.
- Architecture and state ownership are documented in
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Pull requests

- Keep each pull request focused on one feature or fix.
- Add or update documentation when behavior or permissions change.
- Add unit tests for pure logic (MIME parsing, URL validation, theme mapping,
  state transitions) where practical.
- Include manual device-test notes for audio, Live sessions, permissions, or
  device actions; emulator-only results are not enough for those paths.
- Do not add broad package visibility, accessibility, overlay, SMS, or phone
  permissions without a documented product requirement and review.
- Use the repository's existing Kotlin formatting, 2-space indentation, and
  package structure.
- Update `CHANGELOG.md` for user-visible changes.

## Commit guidance

Use imperative, descriptive commit subjects such as `fix: handle Live
reconnection`. Avoid committing generated APKs, local properties, keystores,
API keys, or device logs.

## Reporting issues

Use the issue templates and redact API keys, phone numbers, contacts, audio,
and device logs before posting. Security reports should follow
[SECURITY.md](SECURITY.md) instead of opening a public issue.
