# Contract: Animation Types in toy-config.json

**Branch**: `001-multi-image-animation` | **Date**: 2026-04-08

## Overview

The `animations` array in `toy-config.json` defines the global animation sequence applied to every glyph image. Each entry specifies an animation `type` that maps to a `GlyphAnimation` implementation via the `GlyphToyService.createAnimation()` factory.

## Animation Entry Schema

```json
{
  "type": "<ANIMATION_TYPE>",
  "frameIntervalMs": <integer>,
  "trailingIntervalMs": <integer>
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `type` | string | Yes | Must match a registered type (see below) | Animation implementation identifier |
| `frameIntervalMs` | integer | Yes | >= 30 (enforced by parser) | Milliseconds between frames |
| `trailingIntervalMs` | integer | No | >= 0; default 0 | Milliseconds to pause after animation completes |

## Registered Animation Types

### Existing

| Type String | Class | Frames | Default Interval | Description |
|-------------|-------|--------|-----------------|-------------|
| `SCANLINE` | `ScanlineAnimation` | 13 | 50ms | Single horizontal scanline shift |
| `TRIPLE_SCANLINE` | `TripleScanlineAnimation` | 13 | 50ms | Three alternating-direction scanlines |

### New (this plan)

| Type String | Class | Frames | Default Interval | Description |
|-------------|-------|--------|-----------------|-------------|
| `CRT_SHIMMER` | `CrtShimmerAnimation` | 24 | 67ms | Alternating-row brightness breathing (1.6s cycle) |
| `PHOSPHOR_TRAIL` | `PhosphorTrailAnimation` | 15 | 50ms | Top-to-bottom sweep with 2-row fading trail (750ms) |
| `GLITCH_TEAR` | `GlitchTearAnimation` | 6 | 80ms | Middle-band horizontal displacement, 2 glitch frames (480ms) |

## Fallback Behavior

Unknown type strings fall back to `SCANLINE` with a log warning (`Log.w`). This prevents config errors from crashing the service.

## Timing Interaction

Total time an animation occupies = `frameCount * frameIntervalMs + trailingIntervalMs`

| Type | Total with defaults | With 5s trailing |
|------|-------------------|------------------|
| `CRT_SHIMMER` | 1608ms + trailing | 6608ms |
| `PHOSPHOR_TRAIL` | 750ms + trailing | 5750ms |
| `GLITCH_TEAR` | 480ms + trailing | 5480ms |
| `SCANLINE` | 650ms + trailing | 5650ms |
| `TRIPLE_SCANLINE` | 650ms + trailing | 5650ms |

## Example: Full Sequence

```json
{
  "animations": [
    { "type": "CRT_SHIMMER", "frameIntervalMs": 67, "trailingIntervalMs": 3000 },
    { "type": "PHOSPHOR_TRAIL", "frameIntervalMs": 50, "trailingIntervalMs": 4000 },
    { "type": "GLITCH_TEAR", "frameIntervalMs": 80, "trailingIntervalMs": 2000 }
  ]
}
```

This sequence plays on each image: shimmer (4.6s) -> trail (4.75s) -> glitch (2.48s) -> cross-fade to next image. Total per image: ~11.8s + cross-fade.
