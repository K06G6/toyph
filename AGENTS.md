# AGENTS.md

Project context for AI agents working on Toyph.

## What This Is

Toyph is an Android Kotlin AOD (Always-on Display) glyph toy for
Nothing Phone's 13x13 LED matrix. It uses the GlyphMatrix SDK 2.0
to drive 169 LEDs at 12-bit brightness (0-4095). The active branch
`002-rendering-pipeline-refactor` is splitting a monolithic animation
loop into a layered rendering pipeline with five concepts: sequence
items, effects, transitions, filters, and a pipeline orchestrator.

## Hard Constraints

Violating any of these produces silent bugs (visual corruption, battery
drain, or SDK crashes) that are extremely difficult to diagnose on-device.

### Grid

- Every frame is `IntArray(169)` — exactly `GRID_SIZE * GRID_SIZE` where `GRID_SIZE = 13`.
- Brightness per pixel: `0..4095` (`MAX_BRIGHTNESS`). Clamp with `.coerceIn(0, MAX_BRIGHTNESS)`.
- Pixel index = `row * GRID_SIZE + col`, row-major. Row and col are each `0..12`.
- Never allocate a frame of any other size. Never write beyond index 168.

### Battery

- Frame intervals MUST be >= 30ms. The constant is `MIN_FRAME_INTERVAL = 30L`.
- Inside any per-frame code path: exactly ONE `IntArray.copyOf()`. No other object creation.
- Single coroutine loop drives the entire pipeline — never spawn a second coroutine for rendering.

### Copy-on-Write

Every function that receives a frame (`IntArray`) MUST NOT mutate the input.
Call `frame.copyOf()` first, modify the copy, return the copy. This is critical
because the pipeline passes the same content frame to both the effect layer and
(during transitions) the blend function, which needs the original intact.

## Reference Documents

Read these when you need deeper context. Do not duplicate their content in code comments.

| Document | Path | When to read |
|----------|------|--------------|
| Constitution (binding) | `.specify/memory/constitution.md` | Before any PR; 5 principles govern all code |
| Feature spec | `specs/002-rendering-pipeline-refactor/spec.md` | For acceptance scenarios and requirements |
| Data model | `specs/002-rendering-pipeline-refactor/data-model.md` | For interface signatures and state machines |
| Config contract | `specs/002-rendering-pipeline-refactor/contracts/toy-config-v1.md` | For JSON schema and validation rules |
| Task list | `specs/002-rendering-pipeline-refactor/tasks.md` | For task dependencies and [P] parallel markers |
| Research decisions | `specs/002-rendering-pipeline-refactor/research.md` | For architectural rationale (single loop, copy-on-write) |
| Implementation plan | `specs/002-rendering-pipeline-refactor/plan.md` | For phase ordering and project structure |

## Rendering Pipeline

Single coroutine loop. Each tick:

```
contentFrame = sequencePlayer.getCurrentFrame(elapsedMs)   // content layer
effectFrame  = effectCycler.apply(contentFrame, elapsedMs)  // effect layer
finalFrame   = filterChain.apply(effectFrame)               // filter layer
manager.setMatrixFrame(finalFrame)                          // display
delay(frameInterval)
```

- Each component is nullable. If absent, pass the frame through unchanged.
- `frameInterval` = minimum of all active component intervals.
- Final output MUST be clamped to `0..MAX_BRIGHTNESS` before `setMatrixFrame`.
- Sequence and effects advance on independent elapsed-time clocks.
- Effects MUST NOT reset when the sequence item changes.

## The Four Interfaces

All interfaces live under `app/src/main/java/com/toyph/app/`.
Use the `GRID_SIZE` and `MAX_BRIGHTNESS` constants — never use `13` or `4095` as literals.

### SequenceItem (`sequence/SequenceItem.kt`)

Produces content frames. Implementations: `GlyphItem`, `ScrollingTextItem`.

```kotlin
interface SequenceItem {
    val displayDurationMs: Long   // how long to show (Long.MAX_VALUE = indefinite)
    val frameIntervalMs: Long     // how often frame changes (Long.MAX_VALUE = static)
    val frameCount: Int           // total frames (1 for static glyphs)
    fun getFrame(tick: Int): IntArray  // returns NEW array, size 169
}
```

### Effect (`effects/Effect.kt`)

Modifies a content frame with a visual overlay. One effect active at a time;
effects cycle sequentially through the configured list.

```kotlin
interface Effect {
    val frameIntervalMs: Long       // how often the effect advances
    val frameCount: Int             // frames in one effect cycle
    val trailingIntervalMs: Long    // rest after cycle before next effect
    fun apply(frame: IntArray, tick: Int): IntArray  // copy-on-write
}
```

**Migration pattern from old animations:**
- Old: constructor receives `baseImage: IntArray`, stored as field.
  `generateFrame(tick)` calls `baseImage.copyOf()`.
- New: no stored base image. `apply(frame, tick)` receives the current
  content frame. Call `frame.copyOf()`, apply same mutation logic to the
  copy, return it. Everything else (row shifting, brightness boosting,
  frame count) stays identical.

### Transition (`transitions/Transition.kt`)

Blends two frames during sequence item handoff.

```kotlin
interface Transition {
    val frameIntervalMs: Long
    val frameCount: Int
    fun blend(source: IntArray, target: IntArray, tick: Int): IntArray
}
```

- `FadeTransition.blend()`: `(source[i] * (frameCount - tick) + target[i] * tick) / frameCount`,
  clamped to `0..MAX_BRIGHTNESS`.
- `NoneTransition`: `frameCount = 0`, returns `target.copyOf()`.

### Filter (`filters/Filter.kt`)

Stateless per-pixel transform on the final frame.

```kotlin
interface Filter {
    fun apply(frame: IntArray): IntArray  // returns NEW array, size 169
}
```

Filters are applied in config order by `FilterChain`. Each filter receives
the output of the previous filter. Must clamp output to `0..MAX_BRIGHTNESS`.

## Package Layout (target state)

```
app/src/main/java/com/toyph/app/
  GlyphToyService.kt          # service: parse config -> build pipeline -> start/stop
  ToyConfig.kt                # v1.0.0 config parsing, data classes
  pipeline/
    RenderingPipeline.kt      # single coroutine loop
    SequencePlayer.kt         # time-based content sequencing + transitions
    EffectCycler.kt           # independent effect cycling
    FilterChain.kt            # ordered filter application
  sequence/
    SequenceItem.kt           # interface
    GlyphItem.kt
    ScrollingTextItem.kt
  effects/
    Effect.kt                 # interface
    ScanlineEffect.kt
    TripleScanlineEffect.kt
    PhosphorTrailEffect.kt
    GlitchTearEffect.kt
    CrtShimmerEffect.kt
  filters/
    Filter.kt                 # interface
    BrightnessFilter.kt
    DeadPixelFilter.kt
    BlurFilter.kt
  transitions/
    Transition.kt             # interface
    FadeTransition.kt
    NoneTransition.kt
  util/
    TextBitmapRenderer.kt     # moved from animations/, logic unchanged
```

The old `animations/` package is kept as reference during development
and deleted in Phase 8 (task T032).

## Config: v1.0.0

The config file is `app/src/main/assets/toy-config.json`. See
`specs/002-rendering-pipeline-refactor/contracts/toy-config-v1.md`
for the full schema. Key points:

- `version` MUST be `"1.0.0"`. Older versions produce a specific error with upgrade instructions.
- Sections `sequence`, `effects`, `filters` are each optional. Missing = layer disabled.
- `defaultTransition` provides fallback transition settings; sequence items may override per-item.
- All `frameIntervalMs` values are clamped to minimum 30ms during parsing.

## Code Conventions

- Package: `com.toyph.app.<layer>` (e.g., `com.toyph.app.effects`)
- No wildcard imports
- Use `GRID_SIZE` and `MAX_BRIGHTNESS` constants, never literals
- Pixel indexing: `row * GRID_SIZE + col`
- Brightness clamping: `.coerceIn(0, MAX_BRIGHTNESS)` or `.coerceAtMost(MAX_BRIGHTNESS)`
- Dependencies: GlyphMatrix SDK 2.0, kotlinx-coroutines-android 1.9.0, org.json. No others.
- Build: `./gradlew installDebug` — must compile without warnings
- Testing: on-device visual verification (Nothing Phone 4a Pro). Emulators do not support GlyphMatrix SDK.
  Unit tests (JVM) can cover pipeline logic, effects, filters, and config parsing.

## Common Mistakes

1. **Mutating the input frame** — always `copyOf()` first, return the copy.
2. **Frame size != 169** — every `IntArray` passed to `setMatrixFrame` must be exactly 169.
3. **Brightness > 4095 or < 0** — clamp after any multiplication or addition.
4. **Using `13` or `4095` as literals** — import and use the constants.
5. **Resetting effect state on sequence change** — effects cycle independently.
6. **Allocating objects in the frame loop** — one `copyOf()` per frame, nothing else.
7. **Spawning additional coroutines** — single loop, time-based state tracking.
8. **Hardcoding patterns in Kotlin** — brightness data belongs in JSON assets.
