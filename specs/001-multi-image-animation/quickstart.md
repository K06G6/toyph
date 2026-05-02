# Quickstart: Multi-Image Animation Sequencing

**Branch**: `001-multi-image-animation` | **Date**: 2026-04-08

## Overview

This feature transforms the single-image glyph toy into a configurable sequencer. The service reads `toy-config.json` from assets, defining which glyph images to display and which animations to run on each, with per-animation trailing pauses and cross-fade transitions between images.

## Setup

No additional dependencies. The feature uses existing project infrastructure (Android assets, coroutines, GlyphMatrix SDK).

## Configuration

Edit `app/src/main/assets/toy-config.json`:

```json
{
  "version": "0.1.0",
  "crossFadeDurationMs": 1000,
  "crossFadeFrameIntervalMs": 50,
  "images": [
    "glyphs/default.json",
    "glyphs/pattern2.json"
  ],
  "animations": [
    { "type": "SCANLINE", "frameIntervalMs": 50, "trailingIntervalMs": 5000 },
    { "type": "TRIPLE_SCANLINE", "frameIntervalMs": 50, "trailingIntervalMs": 5000 }
  ]
}
```

### Top-Level Fields

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `crossFadeDurationMs` | No | 1000 | Duration of fade transition between images |
| `crossFadeFrameIntervalMs` | No | 50 | Frame interval during cross-fade |
| `images` | Yes | -- | Ordered list of glyph JSON asset paths |
| `animations` | Yes | -- | Global animation list run on every image |

### Animation Fields

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `type` | Yes | -- | Animation type name (e.g., `SCANLINE`, `TRIPLE_SCANLINE`) |
| `frameIntervalMs` | Yes | -- | Time between frames (ms, minimum 30) |
| `trailingIntervalMs` | No | 0 | Pause after this animation completes (ms) |

### Animation Types

| Type | Description | Default Interval | Frames | Character |
|------|-------------|-----------------|--------|-----------|
| `SCANLINE` | Single-row scanline sweeping across the grid | 50ms | 13 | Classic |
| `TRIPLE_SCANLINE` | Three-row scanline with alternating directions | 50ms | 13 | Active |
| `CRT_SHIMMER` | Alternating-row brightness breathing | 67ms | 24 | Subtle, ambient |
| `PHOSPHOR_TRAIL` | Top-to-bottom sweep with fading echo | 50ms | 15 | Retro, radar |
| `GLITCH_TEAR` | Brief horizontal band displacement | 80ms | 6 | Glitch, digital |

### Example: Using the New Animations

```json
{
  "version": "0.1.0",
  "crossFadeDurationMs": 1000,
  "crossFadeFrameIntervalMs": 50,
  "images": [
    "glyphs/default.json",
    "glyphs/pattern2.json"
  ],
  "animations": [
    { "type": "CRT_SHIMMER", "frameIntervalMs": 67, "trailingIntervalMs": 3000 },
    { "type": "PHOSPHOR_TRAIL", "frameIntervalMs": 50, "trailingIntervalMs": 4000 },
    { "type": "GLITCH_TEAR", "frameIntervalMs": 80, "trailingIntervalMs": 5000 }
  ]
}
```

**CRT Shimmer** adds ambient life to a logo without destroying readability. Best with longer trailing intervals (3–5s) so the breathing effect has time to be appreciated.

**Phosphor Trail** is dramatic — the bright sweep draws the eye. Pair with a generous trailing interval so the base image is visible between sweeps.

**Glitch Tear** is punchy and brief. The 480ms cycle means even a 2s trailing interval gives plenty of calm time between glitches.

## Playback Sequence

For a config with 2 images and 2 animations:

```
Image 1:
  -> Animation 1 plays (all frames)
  -> Pause (animation 1's trailingIntervalMs)
  -> Animation 2 plays (all frames)
  -> Pause (animation 2's trailingIntervalMs)
  -> Cross-fade to Image 2 (1000ms)
Image 2:
  -> Animation 1 plays (all frames)
  -> Pause (animation 1's trailingIntervalMs)
  -> Animation 2 plays (all frames)
  -> Pause (animation 2's trailingIntervalMs)
  -> Cross-fade to Image 1 (1000ms)
(loops forever)
```

## Adding a New Animation

1. Create a new class implementing `GlyphAnimation` in `com.toyph.app.animations`
2. Add a case in `GlyphToyService.createAnimation()` mapping the type name
3. Reference it by type name in `toy-config.json`

## Adding a New Glyph Pattern

1. Create a JSON file in `app/src/main/assets/glyphs/` with 169 brightness values
2. Add the asset path to the `images` list in `toy-config.json`

## Build & Deploy

```bash
./gradlew installDebug
```

Activate: Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy > Toyph
