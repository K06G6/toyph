# Data Model: Multi-Image Animation Sequencing

**Branch**: `001-multi-image-animation` | **Date**: 2026-04-08

## Entities

### ToyConfig

Top-level configuration for the sequencer. Loaded from `assets/toy-config.json`.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| version | String | Config format version | Semantic version string |
| crossFadeDurationMs | Long | Duration of image cross-fade (ms) | >= 0; default 1000 |
| crossFadeFrameIntervalMs | Long | Frame interval during cross-fade (ms) | >= 30 (Battery Discipline) |
| images | List&lt;String&gt; | Ordered asset paths to glyph JSON files | May be empty (blank grid) |
| animations | List&lt;AnimationConfig&gt; | Global animation list applied to every image | May be empty (static display) |

### AnimationConfig

Configuration for a single animation in the global list.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| type | String | Animation type name (mapped in `createAnimation()`) | Must match a known type |
| frameIntervalMs | Long | Time between frames (ms) | >= 30 (Battery Discipline) |
| trailingIntervalMs | Long | Pause after this animation completes (ms) | >= 0; default 0 |

### GlyphImage

A brightness map loaded from a JSON asset file in `assets/glyphs/`. (Existing entity, unchanged.)

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| device | String | Target device identifier | Informational |
| gridSize | Int | Grid dimension (always 13) | Must be 13 |
| version | String | Pattern format version | Informational |
| brightness | IntArray | 169 brightness values, row-major | Length == 169, values 0-4095 |

### GlyphAnimation (interface)

Defined in `com.toyph.app.animations.GlyphAnimation`.

| Field | Type | Description |
|-------|------|-------------|
| frameIntervalMs | Long | Time between frames |
| frameCount | Int | Number of frames in one animation pass |
| generateFrame(tick) | IntArray | Produce frame for given tick |
| baseFrame() | IntArray | Static base image |

Constants: `GRID_SIZE = 13`, `MAX_BRIGHTNESS = 4095`

### CrossFadeTransition (implements GlyphAnimation)

Defined in `com.toyph.app.animations.CrossFadeTransition`.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| sourceImage | IntArray | Outgoing image brightness | Length == 169 |
| targetImage | IntArray | Incoming image brightness | Length == 169 |
| frameIntervalMs | Long | Frame interval during fade | >= 30 |
| frameCount | Int | Computed: durationMs / frameIntervalMs | Derived |

## Relationships

```
ToyConfig
  ├── images: [asset path] ──loads──> GlyphImage (0..N)
  └── animations: [AnimationConfig] ──creates──> GlyphAnimation (0..N)

GlyphToyService
  ├── owns ToyConfig (loaded once at bind)
  ├── for each GlyphImage:
  │     runs each GlyphAnimation from global list
  │     pauses animConfig.trailingIntervalMs after each animation
  └── between GlyphImages:
        runs CrossFadeTransition (sourceImage → targetImage)
```

### CrtShimmerAnimation (implements GlyphAnimation)

Defined in `com.toyph.app.animations.CrtShimmerAnimation`. Alternating-row brightness modulation that "breathes."

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| baseImage | IntArray | Source glyph brightness data | Length == 169 |
| frameIntervalMs | Long | Time between frames | >= 30; default 67 |
| frameCount | Int | Frames per breath cycle | Fixed: 24 |

**Internal constants**: `AMPLITUDE = 0.15`

**Frame generation**: Even rows modulated by `(1 + 0.15 × sin(2π × tick / 24))`, odd rows by `(1 - 0.15 × sin(2π × tick / 24))`. All values clamped to 0–4095.

### PhosphorTrailAnimation (implements GlyphAnimation)

Defined in `com.toyph.app.animations.PhosphorTrailAnimation`. Top-to-bottom sweep with fading echo.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| baseImage | IntArray | Source glyph brightness data | Length == 169 |
| frameIntervalMs | Long | Time between frames | >= 30; default 50 |
| frameCount | Int | Frames per sweep | Fixed: GRID_SIZE + 2 = 15 |

**Internal constants**: `SWEEP_BOOST = 1.5`, `TRAIL1_BOOST = 1.25`, `TRAIL2_BOOST = 1.10`

**Frame generation**: Row at `tick` gets 1.5× boost, row at `tick-1` gets 1.25×, row at `tick-2` gets 1.10×. All other rows at base brightness. Values clamped to 0–4095.

### GlitchTearAnimation (implements GlyphAnimation)

Defined in `com.toyph.app.animations.GlitchTearAnimation`. Brief horizontal band displacement.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| baseImage | IntArray | Source glyph brightness data | Length == 169 |
| frameIntervalMs | Long | Time between frames | >= 30; default 80 |
| frameCount | Int | Frames per cycle | Fixed: 6 |

**Internal constants**: `BAND_START = 4`, `BAND_END = 8` (middle band rows 4–8)

**Frame generation**: Frames 0–3 return base image copy. Frame 4: middle band shifted right 1px (zero-fill left edge). Frame 5: middle band shifted left 1px (zero-fill right edge). All via `System.arraycopy()` on the frame copy.

## Relationships

```
ToyConfig
  ├── images: [asset path] ──loads──> GlyphImage (0..N)
  └── animations: [AnimationConfig] ──creates──> GlyphAnimation (0..N)
        AnimationConfig.type mappings:
          "SCANLINE"         → ScanlineAnimation
          "TRIPLE_SCANLINE"  → TripleScanlineAnimation
          "CRT_SHIMMER"      → CrtShimmerAnimation
          "PHOSPHOR_TRAIL"   → PhosphorTrailAnimation
          "GLITCH_TEAR"      → GlitchTearAnimation

GlyphToyService
  ├── owns ToyConfig (loaded once at bind)
  ├── for each GlyphImage:
  │     runs each GlyphAnimation from global list
  │     pauses animConfig.trailingIntervalMs after each animation
  └── between GlyphImages:
        runs CrossFadeTransition (sourceImage → targetImage)
```

## State Transitions

```
Service States:
  IDLE ──(onBind)──> LOADED ──(EVENT_AOD/EVENT_CHANGE)──> ANIMATING ──(onUnbind)──> IDLE

Sequencer States (within ANIMATING):
  PLAYING_ANIMATION ──(all frames done)──> RESTING
  RESTING ──(trailingInterval elapsed, more animations)──> PLAYING_ANIMATION
  RESTING ──(trailingInterval elapsed, no more animations, single image)──> PLAYING_ANIMATION (restart list)
  RESTING ──(trailingInterval elapsed, no more animations, multiple images)──> CROSS_FADING
  CROSS_FADING ──(fade complete)──> PLAYING_ANIMATION (next image, first animation)
```
