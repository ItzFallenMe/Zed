# Documentation

Start here if you are changing Z.E.D. rather than just using it.

- [Architecture](ARCHITECTURE.md) — state ownership, Live lifecycle, tool
  boundary, creator gate, presence services, and privacy layers.
- [Design system](DESIGN.md) — black-first Material 3, optional liquid glass,
  robot-head states, palettes, type, components, and copy.
- [Releasing](RELEASING.md) — versioning, signing, tags, CI, and the release
  checklist for 2.0.0.
- [Contributing](../CONTRIBUTING.md) — setup, code layout, tests, commit style,
  and review expectations.
- [Security](../SECURITY.md) — reporting, secret handling, creator-code notes,
  and threat boundaries.
- [Code of Conduct](../CODE_OF_CONDUCT.md) — community expectations.
- [Third-party notices](../THIRD_PARTY_NOTICES.md) — robot-head, React, Vosk,
  speaker model, and dependency licensing.
- [Changelog](../CHANGELOG.md) — 2.0.0 and the consolidated 1.x milestones.

## Quick map

| Question | File |
| --- | --- |
| Where does conversation state live? | `ai/ZedEngine.kt` |
| Where is the Live protocol built? | `ai/GeminiLiveClient.kt` |
| Where is the creator code checked? | `data/CreatorAuth.kt`, `data/CreatorGate.kt` |
| Where is the creator speech prompt? | `audio/CreatorChallengeSession.kt` |
| Where are voice presets defined? | `data/VoiceProfiles.kt` |
| Where is the robot rendered? | `ui/ZedRobotAvatar.kt`, `assets/robot-head/` |
| Where is the wake word detected? | `ZedWakeWordService.kt`, `audio/` |
| Where is accessibility used? | `automation/ZedAccessibilityService.kt` |
| Where is the overlay? | `overlay/ZedOverlayService.kt` |
| Where is the theme? | `ui/theme/` |
| Where are the tests? | `app/src/test/java/com/zed/assistant/` |

## Build commands

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:lintDebug
```

The project targets Android 8.0 (API 26) and compiles against SDK 36. Physical
device testing is required for audio, permissions, accessibility, overlay, and
device actions.
