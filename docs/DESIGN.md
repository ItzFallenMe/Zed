# Design system

Z.E.D. is a black-first Android assistant with a robot-head identity, a calm
operator surface, and one optional layer of optical depth. Material 3 is the
default. Liquid glass is a switch, not a requirement.

## Principles

1. **The robot is the product.** The avatar is the clearest signal of what the
   assistant is doing. Every state change is visible before the user reads a
   label.
2. **Black is the canvas.** Obsidian uses true black (`#000000`) with silver
   accents. The interface should feel like a quiet instrument panel, not a
   colourful dashboard.
3. **Material 3 first.** Solid surfaces, standard elevation, predictable touch
   targets, and no ambient animation loops are the baseline. Glass is an
   enhancement for devices that can afford it.
4. **One clear action per area.** Home exposes voice. Chat exposes the composer.
   Settings groups related controls into focused dialogs.
5. **Honest metadata.** Status text reflects the engine. Actions report dialer
   fallback, missing WhatsApp, denied permissions, or ambiguous contacts rather
   than inventing success.
6. **Accessible by default.** Icon buttons carry content descriptions, touch
   targets stay at or above 44 dp, and the robot exposes a semantic state label.

## Two surface modes

### Material 3 (default)

- Flat theme background.
- Solid cards with a hairline outline and low elevation.
- Solid primary voice button.
- No glow fields, no curved dock trail, no translucent blur.
- Lower GPU and battery cost; the right default for mid-range phones.

The preference lives in `ZedPreferences.KEY_LIQUID_GLASS` and is exposed as
`LocalLiquidGlassEnabled`. Shared components read it; screens do not branch on
it individually.

### Liquid glass (optional)

- Translucent gradient cards and dock.
- Hairline specular borders and a soft shadow.
- Three ambient glow fields behind content.
- Curved light trail above the dock.
- The elevated voice action uses a silver gradient.

The mode is persisted and can be changed at runtime from Settings → Appearance.
It is intentionally off on first launch.

## Palettes

| Mode | Background | Accent | Character |
| --- | --- | --- | --- |
| Obsidian black (default) | `#000000` | `#F2F2F2` silver | true-black, quiet, high contrast |
| Pearl light | `#EEF2F9` | `#2F6BFF` | clean, cool, daylight |
| Ruby red | `#0E0307` | `#FF4D5E` | deep red, high energy, late night |
| Sunset glass | `#FFF0EA` | `#FF6A3D` | warm, editorial, soft |

Each palette defines background, card, glass start/end, border, dock, text,
accent, status, glow, and specular tokens in `ui/theme/Color.kt`. Components
read `LocalZedPalette`; no screen hard-codes a brand colour.

An unknown stored theme key falls back to Obsidian black, not to light.

## Robot-head avatar

The robot head is rendered in a local, network-disabled WebView loaded from
`assets/robot-head/avatar.html`. Native code calls `setRobotState()` as the
engine changes state.

| State | Expression / motion |
| --- | --- |
| `idle` | Soft breathing, relaxed eyes |
| `thinking` | Amber screen, scanning pulse |
| `listening` | Green screen, steady attention |
| `speaking` | Live audio-level pulse |
| `searching` | Cyan sweep |
| `working` | Progress ticks |
| `happy` | Pink screen, lifted eyes |
| `error` | Red screen, flat eyes |

The avatar is presentation only. It never receives microphone audio, the Live
socket, or device-action arguments. Tapping it starts a session; long-pressing
the voice control interrupts playback.

## Type

- System sans-serif family.
- Medium/semibold for titles; bold for the wordmark and section labels.
- Uppercase 9–11 sp labels with 1–1.6 sp tracking for metadata.
- Body copy 13–15 sp with 19–21 sp line height.
- The `ZED` wordmark uses a silver-to-grey gradient in Obsidian and the palette
  accent in light modes.

## Components

- `LiquidBackground` — flat theme surface, or tinted page plus three glow fields.
- `GlassCard` — solid Material 3 card or translucent gradient card.
- `GlassIconButton` — 46 dp rounded-square top-bar action.
- `GlassChip` — compact status or metadata pill.
- `SectionLabel` — uppercase group heading.
- `ZedRobotAvatar` — state-driven local WebView.
- `NavigationDock` — five-destination capsule with an elevated voice action.
- `CreatorAccessDialog` — local code challenge, error, retry, and lockout state.
- `PresenceDialogs` — owner voice, overlay style, memory, permissions.

## Navigation

Five destinations share one root:

1. **Home** — robot stage and voice.
2. **Chat** — live and persisted conversation.
3. **Speak** — elevated dock action; starts or stops a session.
4. **Updates** — release notes and capabilities.
5. **Settings** — identity, voice, presence, creator, memory, permissions.

The creator challenge is hosted above navigation so a screen change cannot
dismiss it accidentally.

## Motion and audio

Motion is short and purposeful: state cross-fades, scroll-to-latest, the robot's
expression transitions, and the speaking pulse driven by real audio level. There
are no decorative looping animations in Material 3 mode.

The speaker diagnostic deliberately uses the same `AudioTrack` path as Gemini
audio, so it tests the real output graph rather than a separate sound.

## Voice and copy

Z.E.D. speaks like a partner: young, confident, observant, lightly playful, and
never syrupy. It is concise enough to be spoken aloud. The E.V. Mode preset adds
dry, protective, close energy without copying any film dialogue.

Creator mode is allowed to be more direct and capable, but never claims an
action succeeded without a tool result. The assistant is instructed never to ask
for the creator code in normal conversation.

## Accessibility

- Content descriptions on every icon-only control.
- Semantic labels on the robot and status pill.
- Minimum 44 dp touch targets.
- Text contrast follows the active palette; Obsidian uses near-white on black.
- The creator challenge supports typed entry as well as speech.
- Reduced-motion users still get the state label and screen reader status.
