# Implementation Plan: Three New Animations (CRT Shimmer, Phosphor Trail, Glitch Tear)

**Branch**: `001-multi-image-animation` | **Date**: 2026-04-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-multi-image-animation/spec.md`

## Summary

Add three new `GlyphAnimation` implementations — CRT Shimmer, Phosphor Trail, and Glitch Tear — to the existing multi-image animation sequencing system. Each animation transforms a base glyph image using a distinct visual effect, plugging into the established interface (`GlyphAnimation`), service factory (`createAnimation()`), and configuration (`toy-config.json`). No changes to the animation framework, service loop, or config parser are required.

## Technical Context

**Language/Version**: Kotlin (Android, compileSdk 36, minSdk 34, AGP 9.1.0 built-in Kotlin)
**Primary Dependencies**: GlyphMatrix SDK 2.0 (`.aar`), kotlinx-coroutines-android 1.9.0, `org.json`
**Storage**: JSON asset files (`app/src/main/assets/`)
**Testing**: On-device only (GlyphMatrix SDK requires physical Nothing phone)
**Target Platform**: Nothing Phone 4a Pro, Android 14+ (API 34+)
**Project Type**: Mobile app (Android AOD glyph toy service)
**Performance Goals**: Smooth animation at ≥30ms frame intervals on AOD co-processor
**Constraints**: Single `IntArray.copyOf()` per frame, no object allocation in frame loop, brightness 0–4095, 169-element flat arrays
**Scale/Scope**: 3 new animation classes, 1 service factory update, config additions

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Grid Fidelity | PASS | All animations produce `IntArray(169)`, clamp to 0–4095 |
| II. Battery Discipline | PASS | Default frame intervals ≥50ms (well above 30ms minimum). Per-frame allocation limited to single `IntArray.copyOf()`. CRT Shimmer pre-computes modulation factors; Phosphor Trail uses integer multipliers; Glitch Tear copies and shifts in-place |
| III. SDK Compliance | PASS | No SDK changes; animations plug into existing service |
| IV. Animation as Interface | PASS | Each animation implements `GlyphAnimation`. Added to `createAnimation()` factory. Referenced by type name in `toy-config.json` |
| V. Data-Driven Design | PASS | All animations accept `baseImage: IntArray` as constructor parameter, no direct asset references |

**No violations. Gate passed.**

## Project Structure

### Documentation (this feature)

```text
specs/001-multi-image-animation/
├── plan.md              # This file
├── research.md          # Phase 0: animation design decisions
├── data-model.md        # Phase 1: entity definitions
├── quickstart.md        # Phase 1: integration guide
└── contracts/
    └── toy-config-animations.md  # Updated config contract
```

### Source Code (repository root)

```text
app/src/main/java/com/toyph/app/
├── GlyphToyService.kt           # Update: add 3 cases to createAnimation()
├── ToyConfig.kt                 # No changes needed
└── animations/
    ├── GlyphAnimation.kt        # No changes needed
    ├── CrossFadeTransition.kt   # Existing
    ├── ScanlineAnimation.kt     # Existing
    ├── TripleScanlineAnimation.kt  # Existing
    ├── CrtShimmerAnimation.kt   # NEW
    ├── PhosphorTrailAnimation.kt   # NEW
    └── GlitchTearAnimation.kt   # NEW

app/src/main/assets/
└── toy-config.json              # Update: add new animation entries
```

**Structure Decision**: All new files go in the existing `animations/` package. The only modified files are `GlyphToyService.kt` (factory mapping) and `toy-config.json` (configuration). This follows the established pattern from ScanlineAnimation and TripleScanlineAnimation.

## Animation Designs

### 1. CRT Shimmer (`CRT_SHIMMER`)

**Concept**: Alternating-row brightness modulation that "breathes" — even rows brighten while odd rows dim, then swap. Simulates CRT scan line interference. Subtle enough to preserve logo readability.

**Parameters**:
- `baseImage: IntArray` — the glyph pattern to animate
- `frameIntervalMs: Long` — default 67ms (~15 fps)
- `frameCount: Int` — 24 frames (one full breath cycle ≈ 1.6s)

**Algorithm**:
```
AMPLITUDE = 0.15  (15% brightness modulation)

For each tick 0..<frameCount:
    phase = 2π × tick / frameCount
    evenFactor = 1.0 + AMPLITUDE × sin(phase)
    oddFactor  = 1.0 - AMPLITUDE × sin(phase)
    
    frame = baseImage.copyOf()
    For each pixel at index i:
        row = i / GRID_SIZE
        factor = if (row % 2 == 0) evenFactor else oddFactor
        frame[i] = (baseImage[i] * factor).toInt().coerceIn(0, MAX_BRIGHTNESS)
    
    return frame
```

**Why these values**:
- 15% amplitude: visible shimmer without washing out dim pixels or clipping bright ones (4095 × 1.15 = 4709 → clamped, but only the brightest pixels hit ceiling)
- 24 frames at 67ms = 1.608s cycle: slow enough to read as "breathing," fast enough to feel alive
- Sin wave: smooth continuous modulation with no harsh transitions
- Pre-computed `evenFactor`/`oddFactor`: only 2 float multiplications per frame setup, rest is integer math per pixel

**`baseFrame()`**: returns `baseImage` unchanged

---

### 2. Phosphor Trail (`PHOSPHOR_TRAIL`)

**Concept**: A bright horizontal band sweeps top-to-bottom over the image, leaving a 2-row fading echo behind it. Old-monitor / radar refresh aesthetic.

**Parameters**:
- `baseImage: IntArray` — the glyph pattern to animate
- `frameIntervalMs: Long` — default 50ms
- `frameCount: Int` — `GRID_SIZE + 2` = 15 frames (sweep + trail exit)

**Algorithm**:
```
SWEEP_BOOST = 1.5    (leading edge: 50% brighter)
TRAIL1_BOOST = 1.25  (1 row behind: 25% brighter)
TRAIL2_BOOST = 1.10  (2 rows behind: 10% brighter)

For each tick 0..<frameCount:
    sweepRow = tick
    frame = baseImage.copyOf()
    
    For each pixel at index i:
        row = i / GRID_SIZE
        distance = sweepRow - row
        
        boost = when (distance) {
            0 -> SWEEP_BOOST
            1 -> TRAIL1_BOOST
            2 -> TRAIL2_BOOST
            else -> 1.0
        }
        
        frame[i] = (baseImage[i] * boost).toInt().coerceIn(0, MAX_BRIGHTNESS)
    
    return frame
```

**Why these values**:
- 1.5× sweep: bright enough to be clearly visible, dim pixels become obvious
- Decaying trail (1.25×, 1.10×): natural phosphor persistence look — the echo fades, not cuts
- 15 frames at 50ms = 750ms per sweep: fast, purposeful movement
- Extra 2 frames past GRID_SIZE: lets the trail fully drain off the bottom edge rather than snapping off

**`baseFrame()`**: returns `baseImage` unchanged

---

### 3. Glitch Tear (`GLITCH_TEAR`)

**Concept**: The image is split into 3 horizontal bands. For 2 frames, the middle band is displaced horizontally by 1 pixel, creating visible "tear lines" at the band boundaries. A recovery frame snaps everything back. Mostly calm, brief glitch burst.

**Parameters**:
- `baseImage: IntArray` — the glyph pattern to animate
- `frameIntervalMs: Long` — default 80ms
- `frameCount: Int` — 6 frames (~480ms total cycle)

**Band layout** (13 rows split into 3):
- Top band: rows 0–3 (4 rows) — stays in place
- Middle band: rows 4–8 (5 rows) — displaced during glitch
- Bottom band: rows 9–12 (4 rows) — stays in place

**Algorithm**:
```
For each tick 0..<frameCount:
    frame = baseImage.copyOf()
    
    when (tick) {
        4 -> {
            // Glitch frame 1: middle band shifts RIGHT by 1 pixel
            For each row in 4..8:
                offset = row * GRID_SIZE
                // Shift right: move cols 0..11 to cols 1..12, zero-fill col 0
                arraycopy(frame, offset, frame, offset + 1, GRID_SIZE - 1)
                frame[offset] = 0
        }
        5 -> {
            // Glitch frame 2: middle band shifts LEFT by 1 pixel
            For each row in 4..8:
                offset = row * GRID_SIZE
                // Shift left: move cols 1..12 to cols 0..11, zero-fill col 12
                arraycopy(frame, offset + 1, frame, offset, GRID_SIZE - 1)
                frame[offset + GRID_SIZE - 1] = 0
        }
        else -> { /* frames 0-3: calm base image; frame 5: recovery (base) */ }
    }
    
    return frame
```

**Why these values**:
- 6 frames: 4 calm + 2 glitch = sparingly used, maximum impact
- 80ms frame interval: slow enough that the glitch frames register visually, fast enough that recovery feels instant
- Middle band (rows 4–8): centered on the 13-row grid, displaces the core of most logos
- Right-then-left displacement: creates a "jitter" effect rather than a static shift
- Zero-fill exposed edge: creates the characteristic dark tear line at displacement boundaries
- `System.arraycopy()` on the existing frame array: no extra allocation beyond the initial `copyOf()`

**`baseFrame()`**: returns `baseImage` unchanged

## Complexity Tracking

> No constitution violations. Table not needed.
