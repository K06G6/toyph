# Implementation Plan: Layered Rendering Pipeline Refactor

**Branch**: `002-rendering-pipeline-refactor` | **Date**: 2026-04-09 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-rendering-pipeline-refactor/spec.md`

## Summary

Refactor the monolithic animation loop in `GlyphToyService` into a layered rendering pipeline with five distinct concerns: **Sequence** (glyphs + animations), **Transitions**, **Effects**, and **Filters**. The service currently interleaves image display, animation playback, cross-fade transitions, and scrolling-text special cases in a single nested loop. The new architecture introduces dedicated interfaces for each layer, a time-based pipeline orchestrator, and a new config format with separate sections per layer. The rendering order is: sequence content + transitions → effects → filters → display.

## Technical Context

**Language/Version**: Kotlin (Android, compileSdk 36, minSdk 34, AGP 9.1.0 built-in Kotlin)  
**Primary Dependencies**: GlyphMatrix SDK 2.0 (`.aar`), kotlinx-coroutines-android 1.9.0, `org.json`  
**Storage**: JSON asset files (`app/src/main/assets/`)  
**Testing**: On-device visual verification (no automated test framework; see research.md for unit-test approach)  
**Target Platform**: Nothing Phone 4a Pro (Android 14+, AOD)  
**Project Type**: mobile-app (Android AOD glyph toy)  
**Performance Goals**: ≥30ms frame interval (≤33fps), minimal CPU wake-ups for AOD battery life  
**Constraints**: Single `IntArray.copyOf()` per frame in hot loop; 13×13 grid; 0-4095 brightness; AOD co-processor  
**Scale/Scope**: Single-user device app, ~11 source files, 5 config sections

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Grid Fidelity | PASS | Pipeline output clamped to 169 ints, 0-4095. Each layer enforces this at its boundary. |
| II. Battery Discipline | PASS | Single coroutine loop (not dual). ≥30ms frame interval enforced. One `copyOf()` per frame. Effects/filters operate on the same allocated frame buffer where possible. |
| III. SDK Compliance | PASS | No SDK API changes. `setMatrixFrame(IntArray)` remains the sole display call. |
| IV. Animation as Interface | PASS with evolution | Current principle says "every visual behavior MUST implement GlyphAnimation." The refactor introduces `Effect`, `Filter`, and `Transition` interfaces alongside `SequenceItem`. The spirit (extensibility via interface, service only orchestrates) is preserved and strengthened. Constitution amendment recommended post-merge. |
| V. Data-Driven Design | PASS | New config format remains JSON. Glyph patterns still loaded from asset files. No hardcoded patterns in source. |

No gate violations. Principle IV evolution documented; constitution amendment to be proposed after implementation.

## Project Structure

### Documentation (this feature)

```text
specs/002-rendering-pipeline-refactor/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (config JSON schema)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
app/src/main/
├── assets/
│   ├── glyphs/                          # Glyph JSON files (unchanged)
│   ├── fonts/                           # Font JSON files (unchanged)
│   └── toy-config.json                  # New format (version 1.0.0)
├── java/com/toyph/app/
│   ├── GlyphToyService.kt              # Simplified: load config → create pipeline → start/stop
│   ├── ToyConfig.kt                     # New config parsing (sequence, effects, filters sections)
│   ├── pipeline/
│   │   ├── RenderingPipeline.kt         # Main frame loop: sequence → effects → filters → display
│   │   ├── SequencePlayer.kt            # Time-based content sequencing with transitions
│   │   ├── EffectCycler.kt              # Independent effect cycling on own timer
│   │   └── FilterChain.kt              # Ordered filter application
│   ├── sequence/
│   │   ├── SequenceItem.kt             # Interface: produces content frames
│   │   ├── GlyphItem.kt                # Static glyph with display duration
│   │   └── ScrollingTextItem.kt        # Scrolling text (wraps TextBitmapRenderer)
│   ├── effects/
│   │   ├── Effect.kt                   # Interface: modifies a frame in-place
│   │   ├── ScanlineEffect.kt           # Migrated from ScanlineAnimation
│   │   ├── TripleScanlineEffect.kt     # Migrated from TripleScanlineAnimation
│   │   ├── PhosphorTrailEffect.kt      # Migrated from PhosphorTrailAnimation
│   │   ├── GlitchTearEffect.kt         # Migrated from GlitchTearAnimation
│   │   └── CrtShimmerEffect.kt         # Migrated from CrtShimmerAnimation
│   ├── filters/
│   │   ├── Filter.kt                   # Interface: transforms pixel values
│   │   ├── BrightnessFilter.kt         # Scales all pixels by a factor
│   │   ├── DeadPixelFilter.kt          # Zeroes specific pixel positions
│   │   └── BlurFilter.kt              # Simple neighbor-averaging blur
│   ├── transitions/
│   │   ├── Transition.kt              # Interface: blends two frames over time
│   │   ├── FadeTransition.kt          # Migrated from CrossFadeTransition
│   │   └── NoneTransition.kt          # Instant switch (no blending)
│   └── util/
│       └── TextBitmapRenderer.kt      # Moved from animations/ (unchanged logic)
```

**Structure Decision**: The flat `animations/` package is replaced by a layered package structure (`pipeline/`, `sequence/`, `effects/`, `filters/`, `transitions/`, `util/`). Each package corresponds to one pipeline layer. The old `animations/` package and its files are deleted after migration.

## Complexity Tracking

No constitution violations to justify. Principle IV evolution is additive (more interfaces, same pattern).
