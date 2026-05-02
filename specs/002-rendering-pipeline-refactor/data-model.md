# Data Model: Layered Rendering Pipeline Refactor

**Branch**: `002-rendering-pipeline-refactor` | **Date**: 2026-04-09

## Entities

### SequenceItemConfig

Configuration for one item in the display sequence.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| type | String | Yes | — | `"glyph"` or `"scrolling_text"` |
| path | String | If glyph | — | Asset path to glyph JSON (e.g., `"glyphs/mandalorian.json"`) |
| brightness | Double | No | 1.0 | Brightness multiplier, clamped to [0.0, 1.0] |
| displayDurationMs | Long | If glyph | 5000 | How long to display (glyphs only; animations use their intrinsic length) |
| text | String | If scrolling_text | — | Text to scroll |
| font | String | If scrolling_text | — | Asset path to font JSON |
| frameIntervalMs | Long | If scrolling_text | 80 | Frame interval for scrolling, min 30ms |
| transition | TransitionConfig | No | global default | How this item replaces the previous one |

**Validation**:
- `type` must be one of the recognized values
- `displayDurationMs` must be > 0 if provided (clamped to minimum 100ms)
- `frameIntervalMs` must be ≥ 30ms
- `brightness` clamped to [0.0, 1.0]

### EffectConfig

Configuration for one effect in the effect cycle.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| type | String | Yes | — | `"SCANLINE"`, `"TRIPLE_SCANLINE"`, `"CRT_SHIMMER"`, `"PHOSPHOR_TRAIL"`, `"GLITCH_TEAR"` |
| frameIntervalMs | Long | Yes | — | Frame interval, min 30ms |
| trailingIntervalMs | Long | No | 0 | Rest time after effect completes before next effect starts |

**Validation**:
- `type` must be a recognized effect name
- `frameIntervalMs` ≥ 30ms

### FilterConfig

Configuration for one filter in the filter chain.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| type | String | Yes | — | `"brightness"`, `"dead_pixels"`, `"blur"` |
| value | Double | If brightness | — | Brightness multiplier, clamped to [0.0, 1.0] |
| pixels | List<[Int, Int]> | If dead_pixels | — | List of [row, col] coordinates to zero out |

**Validation**:
- `type` must be a recognized filter name
- `brightness.value` clamped to [0.0, 1.0]
- `dead_pixels.pixels` coordinates must be within [0, 12] for both row and col

### TransitionConfig

Configuration for a transition between sequence items.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| type | String | No | `"fade"` | `"fade"` or `"none"` |
| durationMs | Long | No | global default | Override for transition duration |
| frameIntervalMs | Long | No | 50 | Frame interval during transition, min 30ms |

**Validation**:
- `type` must be `"fade"` or `"none"`
- `durationMs` must be > 0 if provided
- `frameIntervalMs` ≥ 30ms

### ToyConfig (root)

Top-level configuration object.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| version | String | Yes | — | Must be `"1.0.0"` for new format |
| defaultTransition | TransitionConfig | No | fade, 1000ms, 50ms | Global default transition settings |
| sequence | List<SequenceItemConfig> | No | [] | Ordered display sequence |
| effects | List<EffectConfig> | No | [] | Effect cycle configuration |
| filters | List<FilterConfig> | No | [] | Filter chain configuration |

**Validation**:
- `version` must be `"1.0.0"` — older versions produce an error with upgrade instructions
- All sections are optional; empty/missing sections disable that layer

## Interfaces (Runtime)

### SequenceItem

Produces content frames for display.

| Member | Signature | Description |
|--------|-----------|-------------|
| displayDurationMs | `val displayDurationMs: Long` | Total display time (-1 for animation-intrinsic) |
| frameIntervalMs | `val frameIntervalMs: Long` | How often the frame updates (static glyphs: Long.MAX_VALUE) |
| frameCount | `val frameCount: Int` | Total frames (1 for static glyphs) |
| getFrame | `fun getFrame(tick: Int): IntArray` | Returns the frame at the given tick |

### Effect

Modifies a content frame with a visual overlay.

| Member | Signature | Description |
|--------|-----------|-------------|
| frameIntervalMs | `val frameIntervalMs: Long` | How often the effect advances |
| frameCount | `val frameCount: Int` | Total frames in one effect cycle |
| trailingIntervalMs | `val trailingIntervalMs: Long` | Rest time after cycle completes |
| apply | `fun apply(frame: IntArray, tick: Int): IntArray` | Returns modified copy of frame |

### Transition

Blends between two frames over a duration.

| Member | Signature | Description |
|--------|-----------|-------------|
| frameIntervalMs | `val frameIntervalMs: Long` | How often the blend advances |
| frameCount | `val frameCount: Int` | Total frames in transition |
| blend | `fun blend(source: IntArray, target: IntArray, tick: Int): IntArray` | Returns blended frame |

### Filter

Stateless per-pixel transform on the final frame.

| Member | Signature | Description |
|--------|-----------|-------------|
| apply | `fun apply(frame: IntArray): IntArray` | Returns transformed frame |

## State Transitions

### SequencePlayer States

```
[Idle] ──start()──▶ [Playing Item N]
                         │
                         ├── displayDurationMs elapsed
                         │       or animation complete
                         ▼
                    [Transitioning N→N+1]
                         │
                         ├── transition complete
                         ▼
                    [Playing Item N+1]
                         │
                         ├── last item complete
                         ▼
                    [Transitioning Last→0]  (loop)
```

### EffectCycler States

```
[Idle] ──start()──▶ [Playing Effect M]
                         │
                         ├── all frames played
                         ▼
                    [Trailing Delay M]
                         │
                         ├── trailingIntervalMs elapsed
                         ▼
                    [Playing Effect M+1]
                         │
                         ├── last effect complete
                         ▼
                    [Playing Effect 0]  (loop)
```

Both state machines advance based on elapsed time tracked in the single rendering loop.
