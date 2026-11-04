# Design system

Zed's interface follows the Part 7 reference language: soft glass surfaces over
a tinted background, a centred particle orb, compact operator metadata, and a
floating dock with an elevated voice action.

## Principles

1. **The orb is the product.** Every screen leaves the assistant state legible:
   idle, connecting, listening, speaking, or error.
2. **Glass, not noise.** Cards use a low-alpha gradient, a hairline border, and
   a soft shadow. Ambient glow fields sit behind content to give the glass
   something to refract.
3. **One clear action per area.** Home exposes voice, conversation, speaker
   diagnostics, and language. Settings groups related controls instead of
   presenting a wall of fields.
4. **Honest metadata.** Status text reflects the engine. Actions report dialer
   fallback, missing WhatsApp, or ambiguous contacts rather than inventing
   success.
5. **Accessible by default.** Icon buttons carry content descriptions, the orb
   exposes a semantic label, and touch targets stay at or above 44 dp.

## Palettes

| Mode | Background | Accent | Character |
| --- | --- | --- | --- |
| Pearl light | `#EEF2F9` | `#2F6BFF` | clean, product-default |
| Obsidian dark | `#070914` | `#5B8CFF` | low-light, cool glass |
| Ruby red | `#0E0307` | `#FF4D5E` | high-energy, late-night |
| Sunset glass | `#FFF0EA` | `#FF6A3D` | warm, editorial |

Each mode defines background, glass start/end, border, dock, text, accent,
status, and glow tokens in `ui/theme/Color.kt`. Components only read
`LocalZedPalette`; no screen hard-codes a brand colour.

## Type

- System sans-serif family, using medium/semibold weights for titles.
- Uppercase 9–11 sp labels with 1–1.6 sp tracking for metadata.
- Body copy is 13–15 sp with 19–21 sp line height.
- The `ZED` wordmark uses a blue → violet → cyan gradient and wide tracking.

## Components

- `LiquidBackground` — tinted page plus three ambient glow fields.
- `GlassCard` — translucent gradient card with optional click affordance.
- `GlassIconButton` — 46 dp rounded-square top-bar action.
- `GlassChip` — compact status or metadata pill.
- `SectionLabel` — uppercase group heading.
- `ZedOrbView` — local WebGL orb with native tap/long-press detection.
- `ZedBottomDock` — five-destination capsule with lifted mic action.

## Motion and audio

The orb reacts to assistant state and the engine's `level` flow. Compose uses
short cross-fades and scrolling rather than decorative animation loops. The
speaker diagnostic deliberately uses the same `AudioTrack` path as Gemini audio
so the test is meaningful.
