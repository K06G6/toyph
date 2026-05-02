# Config Contract: toy-config.json v1.0.0

**Version**: 1.0.0 | **Replaces**: v0.1.0 (incompatible)

## Schema

```json
{
  "version": "1.0.0",
  "defaultTransition": {
    "type": "fade",
    "durationMs": 1000,
    "frameIntervalMs": 50
  },
  "sequence": [
    {
      "type": "glyph",
      "path": "glyphs/mandalorian.json",
      "brightness": 0.25,
      "displayDurationMs": 10000,
      "transition": {
        "type": "fade",
        "durationMs": 1500
      }
    },
    {
      "type": "scrolling_text",
      "text": "Hello there!",
      "font": "fonts/auraboo.json",
      "brightness": 0.5,
      "frameIntervalMs": 40,
      "transition": {
        "type": "none"
      }
    },
    {
      "type": "glyph",
      "path": "glyphs/rebel.json",
      "brightness": 0.25,
      "displayDurationMs": 8000
    }
  ],
  "effects": [
    {
      "type": "SCANLINE",
      "frameIntervalMs": 50,
      "trailingIntervalMs": 10000
    },
    {
      "type": "PHOSPHOR_TRAIL",
      "frameIntervalMs": 50,
      "trailingIntervalMs": 10000
    }
  ],
  "filters": [
    {
      "type": "brightness",
      "value": 0.8
    },
    {
      "type": "dead_pixels",
      "pixels": [[3, 5], [7, 2]]
    }
  ]
}
```

## Section Rules

| Section | Required | Default when absent |
|---------|----------|-------------------|
| version | Yes | Error if missing or < 1.0.0 |
| defaultTransition | No | `{ "type": "fade", "durationMs": 1000, "frameIntervalMs": 50 }` |
| sequence | No | Empty — black frame displayed |
| effects | No | Empty — no effects applied |
| filters | No | Empty — no filters applied |

## Sequence Item Types

### `glyph`

| Field | Type | Required | Default |
|-------|------|----------|---------|
| type | `"glyph"` | Yes | — |
| path | String | Yes | — |
| brightness | Number (0.0-1.0) | No | 1.0 |
| displayDurationMs | Number (>0) | No | 5000 |
| transition | TransitionConfig | No | uses defaultTransition |

### `scrolling_text`

| Field | Type | Required | Default |
|-------|------|----------|---------|
| type | `"scrolling_text"` | Yes | — |
| text | String (non-empty) | Yes | — |
| font | String | Yes | — |
| brightness | Number (0.0-1.0) | No | 1.0 |
| frameIntervalMs | Number (≥30) | No | 80 |
| transition | TransitionConfig | No | uses defaultTransition |

## Effect Types

Recognized values: `SCANLINE`, `TRIPLE_SCANLINE`, `CRT_SHIMMER`, `PHOSPHOR_TRAIL`, `GLITCH_TEAR`

## Filter Types

| Type | Required Fields | Description |
|------|----------------|-------------|
| `brightness` | `value: Number (0.0-1.0)` | Multiplies all pixel values |
| `dead_pixels` | `pixels: [[row, col], ...]` | Zeroes listed positions |
| `blur` | (none) | Averages with neighboring pixels |

## Transition Types

| Type | Behavior |
|------|----------|
| `fade` | Linear crossfade from previous to current |
| `none` | Instant replacement, no blending |

## Error on Old Format

When `version` is absent or < `"1.0.0"`, parsing MUST fail with:

```
Config version 0.1.0 is no longer supported.
Please update toy-config.json to version 1.0.0.
See: specs/002-rendering-pipeline-refactor/contracts/toy-config-v1.md
Key changes: "images" → "sequence" with type field, "animations" split into "effects" section.
```

## Migration from v0.1.0

| Old field | New equivalent |
|-----------|---------------|
| `images[].path` | `sequence[].path` with `"type": "glyph"` |
| `images[].brightness` | `sequence[].brightness` |
| `crossFadeDurationMs` | `defaultTransition.durationMs` |
| `crossFadeFrameIntervalMs` | `defaultTransition.frameIntervalMs` |
| `animations[]` where type is `SCROLLING_TEXT` | `sequence[]` with `"type": "scrolling_text"` |
| `animations[]` where type is not `SCROLLING_TEXT` | `effects[]` (same fields) |
