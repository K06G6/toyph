# Research: Multi-Image Animation Sequencing

**Branch**: `001-multi-image-animation` | **Date**: 2026-04-08

## R1: Cross-Fade Implementation on LED Grid

**Decision**: Linear brightness interpolation between two 169-element IntArrays over 1000ms at 50ms frame intervals (20 frames).

**Rationale**: Linear interpolation is the simplest approach that produces visually smooth results on a discrete LED grid. At 50ms intervals, 20 interpolation steps provide sufficient granularity. Each frame computes `result[i] = sourceImage[i] * (1 - t) + targetImage[i] * t` where `t` progresses from 0.0 to 1.0. Integer arithmetic avoids floating-point: `result[i] = (source[i] * (steps - step) + target[i] * step) / steps`.

**Alternatives considered**:
- Gamma-corrected interpolation: More perceptually uniform but adds complexity. LED brightness on GlyphMatrix is already hardware-linearized. Rejected for v1.
- Ease-in/ease-out curves: Smoother visual feel but harder to tune without device testing. Can be added as a future enhancement by changing the `t` curve.

## R2: Configuration Format for Toy Sequencer

**Decision**: Single JSON file (`toy-config.json`) in `app/src/main/assets/` (assets root, separate from glyph patterns) with the following structure:

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

**Rationale**: Config lives at assets root to keep the `glyphs/` folder clean for pattern data only. Each animation has its own `trailingIntervalMs` for per-animation pause control. Image paths are relative asset paths. Animation types are referenced by name string mapped in the service factory.

**Alternatives considered**:
- Inline image data in config: Rejected -- violates Data-Driven Design principle (patterns should be separate files).
- Kotlin DSL configuration: Rejected -- requires recompilation for config changes.
- Global animation interval: Initially implemented, then replaced with per-animation `trailingIntervalMs` for finer control.

## R3: Removing cycleDurationMs from GlyphAnimation

**Decision**: Remove `cycleDurationMs` from the `GlyphAnimation` interface. Rest time is configured per-animation via `trailingIntervalMs` in `toy-config.json`. Existing animations (`ScanlineAnimation`, `TripleScanlineAnimation`) drop the constructor parameter.

**Rationale**: The animation's responsibility is frame generation. The sequencer's responsibility is timing and orchestration. This decoupling allows the same animation to be reused with different timing configurations.

**Alternatives considered**:
- Keep `cycleDurationMs` as optional with a default: Rejected -- creates ambiguity about whether the config or the animation controls timing.
- Add a separate `AnimationTiming` wrapper: Over-engineered for the current scope.

## R4: CrossFadeTransition as GlyphAnimation

**Decision**: Implement `CrossFadeTransition(sourceImage: IntArray, targetImage: IntArray, durationMs: Long, frameIntervalMs: Long)` implementing `GlyphAnimation`. The `frameCount` is `durationMs / frameIntervalMs`. Each `generateFrame(tick)` produces the interpolated frame. `baseFrame()` returns the target image (the end state).

**Rationale**: Respects Constitution Principle IV (Animation as Interface) -- all frame generation goes through the `GlyphAnimation` interface. The service can treat cross-fade identically to any other animation in the loop.

**Alternatives considered**:
- Service-level interpolation loop: Violates Principle IV. Frame generation would leak into the service.
- Separate `Transition` interface: Adds another abstraction for a single use case. The existing `GlyphAnimation` interface fits perfectly.

## R5: Sequencer Loop Design

**Decision**: The animation loop in `GlyphToyService` becomes a nested loop:

```
outer: for each image (cycling infinitely)
  inner: for each animation in global list
    play animation frames (tick 0..frameCount-1)
    show base image, delay for animation's trailingIntervalMs
  if multiple images: play CrossFadeTransition to next image
```

**Rationale**: Clean separation of concerns. The outer loop manages image cycling, the inner loop manages animation sequencing. Cross-fade is just another animation played between images.

**Alternatives considered**:
- State machine: More flexible but over-engineered for a linear sequence.
- Coroutine channels: Adds concurrency complexity without benefit since the sequence is strictly linear.

## R6: Battery Impact Assessment

**Decision**: The new sequencer adds no additional frame-rate pressure. Cross-fade at 50ms intervals for 1000ms = 20 extra frames per image transition. Rest periods between animations are pure `delay()` calls with no CPU usage. Total impact is negligible.

**Rationale**: Constitution Principle II (Battery Discipline) requires minimal CPU wake-ups. The cross-fade is a bounded burst (20 frames) followed by long rest periods. Per-animation `trailingIntervalMs` provides explicit rest between animations. Single `IntArray.copyOf()` per frame is maintained in `CrossFadeTransition` -- the interpolation operates on a pre-allocated array.

**Alternatives considered**: N/A -- no battery concern identified.

---

## R7: CRT Shimmer — Modulation Approach

**Decision**: Sinusoidal brightness modulation on alternating rows, 15% amplitude, 24-frame cycle at 67ms.

**Rationale**: A sine wave produces the smoothest perceptual "breathing" with no discontinuities. Alternating even/odd rows with opposing phase creates classic CRT scan-line interference — rows shimmer against each other. At 15%, a pixel at MAX_BRIGHTNESS (4095) peaks at ~4709 → clamped, so only the brightest pixels lose range. Mid-range pixels (2000) modulate 1700–2300, clearly visible. 24 frames × 67ms = 1.6s — in the sweet spot for "breathing" perception (1–3s range), well above 30ms minimum.

Pre-computing `evenFactor` and `oddFactor` once per frame means only 2 float multiplications per frame setup; the per-pixel loop is just integer multiply + clamp.

**Alternatives considered**:
- Triangular wave: Cheaper (no `sin()`) but visible inflection at peaks. At 24 frames the kink is perceptible. Rejected for visual quality.
- Per-pixel noise: More organic but violates Battery Discipline (RNG per pixel per frame). Harder to tune for readability. Rejected.
- Whole-image pulse (uniform breathing): Simpler but lacks CRT aesthetic — no row differentiation. Rejected; doesn't match brief.
- 10% amplitude: Too subtle below ~1000 brightness. 25% amplitude: distorts logo edges, dark pixels flicker on/off. 15% is the balance point.

## R8: Phosphor Trail — Sweep Design

**Decision**: Top-to-bottom sweep with 2-row decaying trail using multiplicative brightness boost (1.5× / 1.25× / 1.10×), 15 frames at 50ms.

**Rationale**: Top-to-bottom matches CRT electron gun direction. The 2-row trail simulates phosphor persistence with 3 graduated brightness levels for smooth perceptual decay. Multiplicative scaling preserves relative contrast — dark pixels stay dark (0 × 1.5 = 0), maintaining logo shape during sweep. 15 frames (GRID_SIZE + 2) lets the trail fully drain off the bottom edge. At 50ms = 750ms total sweep, fast and purposeful.

**Alternatives considered**:
- Bottom-to-top sweep: Unnatural for CRT metaphor. Rejected.
- 1-row trail: Too abrupt — echo doesn't register visually. Rejected.
- 3-row trail: 4-row affected zone covers ~30% of 13-row grid simultaneously. Loses the "sweep" feeling. Rejected.
- Additive brightness boost (+1000): Dark pixels suddenly glow, destroying logo shape. Rejected.
- Set sweep row to MAX_BRIGHTNESS: Too harsh; trail decay from 4095 to base is jarring. Rejected.

## R9: Glitch Tear — Displacement Design

**Decision**: 3-band split (rows 0–3 / 4–8 / 9–12), 1-pixel horizontal displacement of middle band alternating right-then-left, 6-frame cycle (4 calm + 2 glitch) at 80ms.

**Rationale**: The 4+5+4 row split centers the displaced band where most glyph detail concentrates. 1 pixel on a 13-column grid is ~7.7% displacement — visible tear without making the image unrecognizable. Right-then-left alternation across two glitch frames creates a jitter effect that reads as digital instability. 6 frames at 80ms = 480ms; glitch occupies only 160ms (33%), matching "used sparingly." Zero-filling the exposed edge creates the characteristic dark tear line.

Implementation uses `System.arraycopy()` on the already-copied frame array — no allocation beyond the single `IntArray.copyOf()`.

**Alternatives considered**:
- 2-band split: Single tear line, less dramatic. Rejected for lacking intensity.
- Random band positions: Non-deterministic, harder to debug. Rejected.
- 2-pixel displacement: 15.4% of grid width, too aggressive — middle band nearly unrecognizable. Rejected.
- Same direction both glitch frames: Reads as "broken" rather than "glitch." Alternation gives rapid-fire recovery feel.
- 8-frame cycle: Extra recovery frame is redundant (base image return IS recovery). 4-frame cycle: 50% glitch, too aggressive for "sparingly." 6 is the balance.

## R10: Constructor Uniformity

**Decision**: All three animations take `(baseImage: IntArray, frameIntervalMs: Long)` — same signature as existing ScanlineAnimation and TripleScanlineAnimation.

**Rationale**: Keeps `createAnimation()` factory uniform — no special-casing for different constructor shapes. Internal constants (amplitude, boost factors, band boundaries) are private to each class. The `frameIntervalMs` parameter allows config override of the animation's default timing.
