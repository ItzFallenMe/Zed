# Contributing to Zed

## Before you start

Zed asks for powerful permissions (accessibility, notification access, device admin). Keep that
trust in mind in every change:

- Never add telemetry, analytics or a backend.
- Never upload audio, screen content or notification text anywhere.
- Never widen a permission without explaining it in the pull request and in `PRIVACY.md`.

## Development

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

JDK 17+ and Android SDK 36 are required. A device or emulator on API 30+ gives the most realistic
testing, because accessibility gestures, MediaProjection and foreground service types all differ
below that.

## Useful places to start

- `command/IntentParser.kt` — add a Hinglish command; it is pure Kotlin with unit tests.
- `command/ZedTools.kt` — add a Gemini tool; wire it in `CommandExecutor.runTool`.
- `access/ZedAccessibilityService.kt` — screen interaction primitives.

## Pull request checklist

- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] New user-facing intents have a parser test
- [ ] New permissions are documented in `PRIVACY.md`
- [ ] No secrets, keys or personal data in the diff

## Commit style

Short imperative subject, then a paragraph explaining why the change exists.
