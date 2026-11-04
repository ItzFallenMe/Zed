# Changelog

All notable changes to Z.E.D. are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

The 1.x entries below are consolidated milestones from the project's
development cycle. They share the project date so the release history reads as
one continuous line rather than a jump from 1.1.0 to 2.0.0.

## [Unreleased]

## [2.0.0] - 2026-11-04

The first complete Z.E.D. release: a native voice partner with local creator
recognition, hands-free presence, device automation, and a finished identity.

### Added

- Creator protocol with spoken and typed trigger phrases, a local six-digit code
  challenge, PBKDF2-HMAC-SHA256 storage sealed by the Android Keystore, a
  persisted 60-second lockout after three failures, and remembered creator
  access until explicitly locked.
- `creatorControl` tool for guarded master actions: system navigation, lock
  screen, wake word, overlay, theme, memory, conversation history, and locking
  creator access.
- Robot-head avatar as the primary assistant stage, with idle, thinking,
  listening, speaking, searching, working, happy, and error expressions.
- Eight voice presets — Zed Prime, E.V. Mode, Spark, Gravel, Sentinel, Velvet,
  Anchor, and Current — mapping Gemini voices to distinct delivery directions.
- Material 3 as the default interface, with a persisted liquid-glass toggle for
  translucent cards, ambient glow, and the curved dock trail.
- True-black Obsidian as the default palette, with Pearl, Ruby, and Sunset
  alternatives.
- Creator access dialog, voice preset selector, presence section, memory vault,
  owner-voice enrolment, overlay style picker, permissions centre, and wake
  chime toggle in Settings.
- Branded robot-head launcher icon, adaptive foreground, and Home wordmark.
- Release notes screen for 2.0, a complete README, architecture and design
  documentation, security notes, and expanded contributor guidance.

### Changed

- Home and conversation surfaces now lead with the robot-head identity and live
  state instead of a particle orb.
- The dock and shared surfaces fall back to solid Material 3 styling when liquid
  glass is disabled.
- App identity is now **Z.E.D. — Zeta Evolved Device Intelligence**, with the
  creator credited as Vedant (ItzFallenMe).
- Version is `2.0.0` (`versionCode` 20).
- Wake-word and overlay services synchronise on activity resume, so returning
  from a system permission screen immediately reflects the saved preference.

### Removed

- The unused Three.js particle-orb renderer and its vendored `three.min.js`
  asset. The robot-head avatar is the single primary presentation.

### Fixed

- Settings compile issues around the new presence, memory, and permission
  panels.
- Creator challenge state survives configuration changes and no longer leaks
  into the Gemini session.
- Theme fallback remains dark when a stored theme key is unknown.

## [1.9.0] - 2026-11-04

### Added

- Local creator gate with phrase detection, code validation, attempt lockout,
  and an unlock state that persists across restarts.
- Master-control routing for creator-only system and app actions.
- Z.E.D. persona rewrite: creator knowledge, partner-like E.V.-inspired tone,
  safety boundaries, and creator-mode behaviour.
- Voice preset data model and persisted preset selection.

## [1.8.0] - 2026-11-04

### Added

- State-driven robot-head avatar in the Home stage and conversation header.
- Floating robot overlay with selectable avatar skins.
- Overlay permission flow and a Settings entry for overlay style.
- Owner-voice enrolment: three local samples, speaker vectors, and profile
  storage.

## [1.7.0] - 2026-11-04

### Added

- Android accessibility service for reading the current screen, tapping visible
  text, typing into a focused field, scrolling, and locking the screen.
- Screen-aware tool routing and verification after accessibility actions.
- Permissions centre covering microphone, contacts, phone, notifications,
  overlay, accessibility, location, and battery optimisation.

## [1.6.0] - 2026-11-04

### Added

- Offline Vosk wake-word detection with a bundled English model.
- Wake-word foreground service, persisted enable/disable state, and wake chime.
- Lightweight floating-avatar entry point from the wake service.

## [1.5.0] - 2026-11-04

### Added

- Local SQLite conversation history and restoration into the chat screen.
- Memory vault for durable user facts, with clear controls in Settings.
- Local weather lookup through Open-Meteo and device location.
- Conversation clear action and creator-panel history controls.

## [1.4.0] - 2026-11-04

### Added

- Foreground voice-session service with a visible microphone notification.
- Interruption flushing, long-press mute, reconnect attempts with a resumption
  handle, and honest error states.
- Speaker diagnostic through the same `AudioTrack` path used by Gemini audio.

### Fixed

- Typed messages are queued until setup and the current turn complete instead of
  being dropped during connection.

## [1.3.0] - 2026-11-04

### Added

- Validated device actions: open app, WhatsApp prefill, safe links, YouTube,
  calls, dialer fallback, contacts, media, volume, brightness, torch, and time.
- Ambiguity handling for contacts and apps; structured JSON results for every
  tool.
- Bluetooth and communication audio routing.

## [1.2.0] - 2026-11-04

### Added

- Typed turns over the same Gemini Live session as voice.
- Conversation screen with live user/assistant bubbles and an empty state.
- Persisted chat turns and automatic scrolling.
- Persona and transport chips in the composer.

## [1.1.0] - 2026-11-04

### Added

- Liquid-glass Compose design system with reusable cards, icon buttons, chips,
  ambient backgrounds, and a floating dock.
- Pearl, Obsidian, Ruby, and Sunset palettes with persisted selection.
- Grouped Settings dialogs for API/model, persona/voice, language, privacy,
  device actions, theme, and app identity.
- Release guidance, third-party notices, architecture and design docs, and CI
  checks.

### Changed

- Home, Conversation, Settings, and Updates rebuilt around the shared theme.
- Release builds enable R8 and resource shrinking; signing stays opt-in.

## [1.0.0] - 2026-11-04

### Added

- Initial native Android voice assistant with Gemini Live audio streaming, typed
  turns, device actions, foreground session continuity, settings, and
  conversation history.
