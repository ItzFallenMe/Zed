# Changelog

All notable changes to Z.E.D. are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

The 1.x entries below are consolidated milestones from the project's
development cycle. They share the project date so the release history reads as
one continuous line rather than a jump from 1.1.0 to 2.0.0.

## [Unreleased]

## [2.1.3] - 2026-11-04

### Fixed
- Serialized runtime permission requests and prevented cancelled callers from clearing an outstanding Android permission dialog.
- Removed pending location updates when a weather lookup is cancelled or times out.
- Escaped SQL LIKE wildcards in memory search and keyword deletion so `%` and `_` are treated literally.
- Fixed floating overlay clipping when an alternate skin falls back to the bundled robot renderer.
- Prevented concurrent multi-step device automation tasks with an atomic running guard.
- Improved multi-step automation errors when accessibility is enabled but not connected.

- Floating avatar now falls back to the locally bundled robot renderer for skins that still depend on a blocked remote React import.
- Accessibility settings detection now parses Android's enabled service component entries instead of relying on fragile string fragments.
- Device actions now distinguish an enabled accessibility permission whose service is not yet connected from a disabled permission.
- Bottom navigation is explicitly anchored to the bottom center of the app window while respecting system navigation insets.

## [2.1.2] - 2026-11-04

### Changed

- Refined the Home header into a balanced responsive row with truncation for long greetings and smaller-screen spacing.
- Reduced fixed Home screen padding and quick-action card height to give the avatar and status more usable space.
- Updated Home instructions to refer to the robot avatar rather than the removed orb interaction.
- Removed the permanent Updates notification dot so the dock no longer implies unread updates when none are tracked.

## [2.1.1] - 2026-11-04

### Fixed

- Hardened the bundled robot avatar WebView so the UI waits for the canvas renderer, not merely the HTML page, before sending state updates.
- Restricted local WebView file access, disabled unnecessary storage and zoom controls, and added clear main-frame asset failure logging.
- Fixed floating overlay renderer readiness checks and queued avatar state until the renderer is ready.
- Removed the overlay's no-limits window flag and constrained its window to the app display area to reduce off-screen placement and clipping.

### Changed

- Version is `2.1.1` (`versionCode` 22).

## [2.1.0] - 2026-11-04

Advanced automation and presence pass. Every new action reports what it
actually did, including partial handoffs.

### Added

- Bounded `runTask` tool and `AgentTaskEngine` for ordered multi-step phone
  actions: open an app, tap, type, scroll, inspect, wait, and verify expected
  text. Execution stops at the first failure and progress appears in the status
  line and floating overlay.
- WhatsApp direct-send path. `sendWhatsAppMessage` accepts `sendNow`; Z.E.D.
  waits for the compose screen and taps Send through accessibility, returning
  `auto_send_tapped` only when the tap succeeded. Delivery is still confirmed by
  WhatsApp.
- `playYouTube` tool. It opens results and taps the best matching video when
  accessibility is enabled, returning `auto_play_tapped` or a results-page
  fallback.
- Headset-aware audio routing for Bluetooth SCO, wired, USB, and BLE headsets,
  with the loudspeaker used only when no headset is connected.
- Owner-voice wake enforcement. When a profile is enrolled and enabled, the
  wake service checks the recent microphone pre-roll and refuses a mismatched
  voice with a visible notification. Manual taps remain available.
- Live screen context with a real MediaProjection foreground service, a visible
  stop action, 2-second downscaled JPEG frames, and an accessibility-screenshot
  fallback if projection is unavailable.
- Saved-history browser in the chat header, alongside the existing memory vault.
- Optional signing-certificate pinning (`ZED_SIGNING_CERT_SHA256`) that blocks
  creator master control when the installed certificate does not match.
- Settings toggle for live screen context and updated owner-voice copy.

### Changed

- Version is `2.1.0` (`versionCode` 21).
- Session teardown stops screen capture so sharing never outlives the
  conversation that requested it.
- Overlay long-press cancels an active multi-step task before falling back to
  the conversation interrupt.

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
