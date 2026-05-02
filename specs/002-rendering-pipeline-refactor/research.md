# Research: Layered Rendering Pipeline Refactor

**Branch**: `002-rendering-pipeline-refactor` | **Date**: 2026-04-09

## R1: Interface Redesign — Splitting GlyphAnimation

**Decision**: Replace the single `GlyphAnimation` interface with four purpose-specific interfaces: `SequenceItem`, `Effect`, `Transition`, `Filter`.

**Rationale**: The current `GlyphAnimation` interface conflates three different roles:
- **Content production** (ScrollingTextAnimation generates frames from scratch)
- **Frame modification** (ScanlineAnimation takes a base image and modifies it)
- **Frame blending** (CrossFadeTransition interpolates between two images)

These roles have different signatures:
- Content: `fun getFrame(tick: Int): IntArray` — no input frame needed
- Effect: `fun apply(frame: IntArray, tick: Int): IntArray` — takes current frame, returns modified
- Transition: `fun blend(source: IntArray, target: IntArray, progress: Float): IntArray` — takes two frames
- Filter: `fun apply(frame: IntArray): IntArray` — stateless per-pixel transform

Collapsing them into one interface forces workarounds (e.g., `baseFrame()` exists only because effects need to know the original image, but transitions don't use it the same way).

**Alternatives considered**:
- Keep `GlyphAnimation` as a sealed interface with subtypes: rejected because the calling code (pipeline) needs to invoke them differently, so a common interface adds no value.
- Single `FrameProcessor` interface with optional inputs: rejected because it loses type safety and makes the pipeline harder to reason about.

## R2: Single-Loop vs Dual-Coroutine Architecture

**Decision**: Single coroutine loop with time-based state tracking.

**Rationale**: The sequence and effects each have independent timers, suggesting two coroutines. However:
- **Battery**: A single loop means one wake-up cycle. Two coroutines would double CPU wake-ups on AOD, violating Constitution Principle II.
- **Thread safety**: Two coroutines writing to the same frame buffer requires synchronization. On a 13×13 grid this is cheap, but unnecessary complexity.
- **Simplicity**: The loop ticks at the fastest active frame interval (min of sequence item interval, effect interval). Each tick, it checks elapsed time to decide whether the sequence item or effect should advance.

The loop structure:
```
while (active) {
    contentFrame = sequencePlayer.getCurrentFrame(elapsedMs)
    effectFrame = effectCycler.apply(contentFrame, elapsedMs)
    finalFrame = filterChain.apply(effectFrame)
    display(finalFrame)
    delay(frameInterval)
}
```

`sequencePlayer` internally tracks which item is active, whether a transition is playing, and advances based on elapsed time. `effectCycler` tracks which effect is active and its tick position.

**Alternatives considered**:
- Dual coroutines with `Mutex`: rejected for battery cost and complexity.
- Event-driven (no loop, effects trigger their own schedules): rejected because frame display must be synchronized — you can't have an effect and a transition both calling `setMatrixFrame` at different times.

## R3: Old Config Format Detection

**Decision**: Check for `"version"` field value. Old format uses `"0.1.0"` (or absent). New format requires `"1.0.0"`. If version is missing or < 1.0.0, emit a specific error message with upgrade instructions.

**Rationale**: The current config always has `"version": "0.1.0"`. The new format will use `"1.0.0"`. Version checking is reliable because:
- The field already exists in all configs
- A simple string comparison distinguishes old from new
- The error message can include a link to the migration guide or show a diff

**Alternatives considered**:
- Check for presence of `"sequence"` key: rejected because a partially-migrated config might have both old and new keys.
- Auto-detect and migrate: rejected per clarification decision (clean break).

## R4: Effect Frame Modification Pattern

**Decision**: Effects receive the current content frame and return a new modified frame (copy-on-write). The effect does NOT modify the input frame in-place.

**Rationale**: Current effect animations (e.g., `ScanlineAnimation`) call `baseImage.copyOf()` in `generateFrame()` and modify the copy. This pattern is preserved:
- Effects call `frame.copyOf()` internally and modify the copy (satisfies Constitution Principle II: single copyOf per frame).
- The pipeline passes the content frame to the effect; the effect returns a new array.
- This prevents the content frame from being corrupted, which matters during transitions (the source frame must stay intact across multiple blend calls).

The key change: effects no longer receive the base image at construction time. Instead, they receive it each tick via `apply(frame, tick)`. This means effects are content-agnostic — they don't care whether the frame comes from a glyph, scrolling text, or a transition blend.

**Alternatives considered**:
- In-place mutation: rejected because transitions need the original content frame intact.
- Separate overlay buffer composited via addition: rejected because effects like GlitchTear shift pixels (not additive) and scanline boosts brightness (multiplicative), so a simple overlay doesn't work.

## R5: Unit Testing Strategy

**Decision**: Add JUnit 5 unit tests for pipeline logic, config parsing, and individual effects/filters. Tests run on JVM (no Android dependencies needed for pure logic).

**Rationale**: The rendering pipeline is pure computation (IntArray in → IntArray out). None of the pipeline classes depend on Android APIs — only `GlyphToyService` and `TextBitmapRenderer` (which uses `android.content.res.AssetManager`) do. This means:
- `Effect.apply()`, `Filter.apply()`, `Transition.blend()`, `SequencePlayer` state logic, and `ToyConfig.parse()` can all be tested with standard JUnit on the JVM.
- `TextBitmapRenderer` can be tested by extracting the font-parsing logic into a testable class that takes a `String` (JSON) instead of an `AssetManager`.

**Alternatives considered**:
- Android instrumented tests only: rejected because they require a device/emulator and are slow.
- No automated tests: rejected because this is a major refactor and regressions would be invisible until on-device visual testing.
