# Changelog

All notable changes to Zed are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.0] - 2026-11-04

### Added

- Part 7-inspired liquid-glass interface built as a reusable Compose design
  system.
- Pearl, Obsidian, Ruby, and Sunset palettes with persisted selection.
- Offline Three.js particle orb with idle, listening, thinking, and speaking
  states; the shader receives Zed's real session level.
- Floating five-destination dock with an elevated voice action and curved light
  trail.
- Grouped Settings dialogs for API/model, persona/voice, language, privacy,
  device actions, theme, and app identity.
- Release guidance, third-party notices, architecture and design docs, and
  expanded CI checks.

### Changed

- Home, Conversation, Settings, and Updates were rebuilt around the new design
  system.
- The conversation header now reflects session state and the composer exposes
  the active Zed/Live transport.
- Typed messages are queued until the active Live turn finishes instead of
  being dropped mid-turn.
- The app version is now `1.1.0` (`versionCode` 2).
- Release builds enable R8 and resource shrinking; signing is opt-in through
  maintainer-provided environment values.

### Fixed

- Foreground service startup no longer relies on API-gated branches below the
  project's minimum SDK.
- Compose screens compile against the pinned Material 3 baseline.
- Android lint is clean; the build workflow runs tests, assemble, and lint.

## [1.0.0] - 2026-11-04

### Added

- Initial native Android voice assistant with Gemini Live audio streaming,
  typed turns, device actions, foreground session continuity, settings, and
  conversation history.
