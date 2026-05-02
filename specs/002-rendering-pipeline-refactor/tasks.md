# Tasks: Layered Rendering Pipeline Refactor

**Input**: Design documents from `/specs/002-rendering-pipeline-refactor/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/toy-config-v1.md, quickstart.md

**Tests**: Not explicitly requested in spec. Unit tests omitted from task list but recommended per research.md R5.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: Create new package structure and define core interfaces

- [x] T001 Create package directories per plan.md: `app/src/main/java/com/toyph/app/pipeline/`, `sequence/`, `effects/`, `filters/`, `transitions/`, `util/`
- [x] T002 Move `app/src/main/java/com/toyph/app/animations/TextBitmapRenderer.kt` to `app/src/main/java/com/toyph/app/util/TextBitmapRenderer.kt` (update package declaration, keep logic unchanged)
- [x] T003 [P] Define `SequenceItem` interface in `app/src/main/java/com/toyph/app/sequence/SequenceItem.kt` with members: `displayDurationMs: Long`, `frameIntervalMs: Long`, `frameCount: Int`, `getFrame(tick: Int): IntArray`
- [x] T004 [P] Define `Effect` interface in `app/src/main/java/com/toyph/app/effects/Effect.kt` with members: `frameIntervalMs: Long`, `frameCount: Int`, `trailingIntervalMs: Long`, `apply(frame: IntArray, tick: Int): IntArray`
- [x] T005 [P] Define `Transition` interface in `app/src/main/java/com/toyph/app/transitions/Transition.kt` with members: `frameIntervalMs: Long`, `frameCount: Int`, `blend(source: IntArray, target: IntArray, tick: Int): IntArray`
- [x] T006 [P] Define `Filter` interface in `app/src/main/java/com/toyph/app/filters/Filter.kt` with member: `apply(frame: IntArray): IntArray`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T007 Rewrite `app/src/main/java/com/toyph/app/ToyConfig.kt` to parse v1.0.0 config format. New data classes: `SequenceItemConfig`, `EffectConfig`, `FilterConfig`, `TransitionConfig`, `ToyConfig` (with `sequence`, `effects`, `filters`, `defaultTransition` fields). Parse per data-model.md schemas. Reject version < 1.0.0 with error message per contracts/toy-config-v1.md. Apply validation: min 30ms frame intervals, brightness clamping [0.0, 1.0], coordinate bounds for dead pixels.
- [x] T008 Update `app/src/main/assets/toy-config.json` to v1.0.0 format per contracts/toy-config-v1.md. Convert existing `images` entries to `sequence` items with `"type": "glyph"`, move `SCROLLING_TEXT` animations to `sequence` with `"type": "scrolling_text"`, move remaining animations to `effects` section, add `defaultTransition` with current `crossFadeDurationMs`/`crossFadeFrameIntervalMs` values.
- [x] T009 Create `app/src/main/java/com/toyph/app/pipeline/RenderingPipeline.kt`. Single coroutine loop per research.md R2: each tick calls `sequencePlayer.getCurrentFrame(elapsedMs)` → `effectCycler.apply(frame, elapsedMs)` → `filterChain.apply(frame)` → `display(frame)`. Each component is nullable (inactive if absent). Frame interval = minimum of active component intervals. Clamp output to 0-4095.
- [x] T010 Simplify `app/src/main/java/com/toyph/app/GlyphToyService.kt`: remove all animation loop logic, `createAnimation()`, `createScrollingTextAnimation()`, image loading loop. Replace with: parse ToyConfig → construct pipeline components from config → create `RenderingPipeline` → start/stop on events. Keep `loadGlyph()` as a utility. Glyph loading moves into pipeline construction.

**Checkpoint**: Foundation ready — package structure, interfaces, config parsing, pipeline skeleton, and simplified service are in place. User story implementation can now begin.

---

## Phase 3: User Story 1 — Mixed Glyph and Animation Sequence (Priority: P1)

**Goal**: Users can configure a display sequence mixing static glyphs and scrolling text animations, played in order with configurable durations.

**Independent Test**: Define a sequence of [Mandalorian (10s), ScrollingText, Rebel (8s)] in config. Deploy to device. Verify items display in order with correct timing and the sequence loops.

### Implementation for User Story 1

- [x] T011 [P] [US1] Create `app/src/main/java/com/toyph/app/sequence/GlyphItem.kt` implementing `SequenceItem`. Constructor takes `pixels: IntArray` and `displayDurationMs: Long`. `frameCount = 1`, `frameIntervalMs = Long.MAX_VALUE`, `getFrame()` returns `pixels.copyOf()`.
- [x] T012 [P] [US1] Create `app/src/main/java/com/toyph/app/sequence/ScrollingTextItem.kt` implementing `SequenceItem`. Constructor takes `textMatrix: IntArray`, `matrixWidth: Int`, `frameIntervalMs: Long`. `frameCount = matrixWidth - GRID_SIZE + 1`, `displayDurationMs = frameCount * frameIntervalMs`. `getFrame(tick)` extracts 13x13 window at offset `tick` from `textMatrix` (same logic as current `ScrollingTextAnimation.generateFrame()`).
- [x] T013 [US1] Create `app/src/main/java/com/toyph/app/pipeline/SequencePlayer.kt`. Manages ordered list of `SequenceItem`s. Tracks: current item index, elapsed time for current item, current tick. `getCurrentFrame(elapsedMs: Long): IntArray` returns the frame for the active item, advances to next item when duration expires, loops after last item. Handle edge cases: empty sequence → black frame, single item → display indefinitely.
- [x] T014 [US1] Wire SequencePlayer into RenderingPipeline and GlyphToyService: construct `GlyphItem`/`ScrollingTextItem` from `SequenceItemConfig` entries, pass glyph loading (JSON → IntArray with brightness) and text rendering (TextBitmapRenderer) into item construction, create `SequencePlayer` with the item list, pass to `RenderingPipeline`.

**Checkpoint**: Sequence plays glyphs and scrolling text in configured order with correct durations. No effects, filters, or transitions yet — items switch instantly.

---

## Phase 4: User Story 2 — Effects Run Independently (Priority: P1)

**Goal**: Effects (scanline, triple scanline, phosphor trail, glitch tear, CRT shimmer) run on their own cycle, overlaying whatever content is displayed, uninterrupted by sequence changes.

**Independent Test**: Configure a single static glyph with effects [SCANLINE, PHOSPHOR_TRAIL]. Verify effects cycle on their own timing, independent of glyph display.

### Implementation for User Story 2

- [x] T015 [P] [US2] Create `app/src/main/java/com/toyph/app/effects/ScanlineEffect.kt` implementing `Effect`. Migrate logic from `ScanlineAnimation.kt`: `apply(frame, tick)` calls `frame.copyOf()`, shifts row `tick` right, boosts brightness 1.5x, returns modified frame. `frameCount = GRID_SIZE`.
- [x] T016 [P] [US2] Create `app/src/main/java/com/toyph/app/effects/TripleScanlineEffect.kt` implementing `Effect`. Migrate logic from `TripleScanlineAnimation.kt`: three simultaneous scanlines alternating left/right on rows tick, tick+1, tick+2.
- [x] T017 [P] [US2] Create `app/src/main/java/com/toyph/app/effects/PhosphorTrailEffect.kt` implementing `Effect`. Migrate logic from `PhosphorTrailAnimation.kt`: sweeping boost with trailing rows.
- [x] T018 [P] [US2] Create `app/src/main/java/com/toyph/app/effects/GlitchTearEffect.kt` implementing `Effect`. Migrate logic from `GlitchTearAnimation.kt`: shifts band (rows 4-8) over 6-frame sequence.
- [x] T019 [P] [US2] Create `app/src/main/java/com/toyph/app/effects/CrtShimmerEffect.kt` implementing `Effect`. Migrate logic from `CrtShimmerAnimation.kt`: even/odd row brightness oscillation over 24 frames.
- [x] T020 [US2] Create `app/src/main/java/com/toyph/app/pipeline/EffectCycler.kt`. Manages ordered list of `Effect`s. Tracks: current effect index, effect tick, trailing delay elapsed. `apply(frame: IntArray, elapsedMs: Long): IntArray` applies current effect to the frame, advances tick based on elapsed time, transitions to trailing delay after all frames, advances to next effect after trailing delay, loops. If no effects configured, returns frame unchanged. MUST NOT reset when called with a different content frame.
- [x] T021 [US2] Wire EffectCycler into RenderingPipeline and GlyphToyService: construct effect instances from `EffectConfig` entries (factory by type name), create `EffectCycler` with the effect list, pass to `RenderingPipeline`.

**Checkpoint**: Effects cycle independently of the sequence. Content changes don't interrupt or reset effects.

---

## Phase 5: User Story 3 — Transitions Between Sequence Items (Priority: P2)

**Goal**: Each sequence item has a transition parameter defining how it replaces the previous item. Default is fade. Per-item duration override with global default fallback.

**Independent Test**: Configure [Glyph-A with fade(2s), Glyph-B with none]. Verify Glyph-A fades in over 2s, Glyph-B appears instantly.

**Dependencies**: Requires US1 (SequencePlayer) to be complete.

### Implementation for User Story 3

- [x] T022 [P] [US3] Create `app/src/main/java/com/toyph/app/transitions/FadeTransition.kt` implementing `Transition`. Migrate logic from `CrossFadeTransition.kt`: `blend(source, target, tick)` linearly interpolates between source and target frames. `frameCount = durationMs / frameIntervalMs`.
- [x] T023 [P] [US3] Create `app/src/main/java/com/toyph/app/transitions/NoneTransition.kt` implementing `Transition`. `frameCount = 0`, `blend()` returns `target.copyOf()` immediately.
- [x] T024 [US3] Integrate transitions into `app/src/main/java/com/toyph/app/pipeline/SequencePlayer.kt`: when advancing to next item, create the item's configured `Transition` (or global default if not specified). During transition, `getCurrentFrame()` calls `transition.blend(previousFrame, nextFrame, tick)`. After transition completes, switch to normal playback of next item. Support per-item `durationMs` override falling back to `defaultTransition` from config. Add transition factory method (type name → Transition instance).

**Checkpoint**: Transitions render smoothly between sequence items. No black flashes. Effects continue over transitions.

---

## Phase 6: User Story 4 — Filters Apply to Final Output (Priority: P2)

**Goal**: Filters are static post-processing applied to every frame after content + effects. Multiple filters applied in config order.

**Independent Test**: Configure a glyph with brightness filter at 0.5. Verify all output pixels are at half their expected brightness.

### Implementation for User Story 4

- [x] T025 [P] [US4] Create `app/src/main/java/com/toyph/app/filters/BrightnessFilter.kt` implementing `Filter`. Constructor takes `value: Double`. `apply(frame)` multiplies every pixel by `value`, clamps to [0, 4095], returns new array.
- [x] T026 [P] [US4] Create `app/src/main/java/com/toyph/app/filters/DeadPixelFilter.kt` implementing `Filter`. Constructor takes `pixels: List<Pair<Int, Int>>` (row, col coordinates). `apply(frame)` zeroes those positions, returns new array.
- [x] T027 [P] [US4] Create `app/src/main/java/com/toyph/app/filters/BlurFilter.kt` implementing `Filter`. `apply(frame)` averages each pixel with its 4-neighbors (up/down/left/right), clamps to [0, 4095], returns new array. Edge pixels average with fewer neighbors.
- [x] T028 [US4] Create `app/src/main/java/com/toyph/app/pipeline/FilterChain.kt`. Takes ordered list of `Filter`s. `apply(frame: IntArray): IntArray` applies each filter in sequence. If no filters, returns frame unchanged.
- [x] T029 [US4] Wire FilterChain into RenderingPipeline and GlyphToyService: construct filter instances from `FilterConfig` entries (factory by type name), create `FilterChain`, pass to `RenderingPipeline`.

**Checkpoint**: Filters modify every output frame. Multiple filters compose in order. Pipeline: content → effects → filters → display.

---

## Phase 7: User Story 5 — Separate Configuration Sections (Priority: P3)

**Goal**: Each layer has its own config section; all sections are independently optional. Old format rejected with clear error.

**Independent Test**: Provide configs with various section combinations (sequence only, effects only, all sections, no sections). Verify each works or produces appropriate defaults/errors.

**Dependencies**: All other stories provide the components; this story validates the config layer handles all combinations.

### Implementation for User Story 5

- [x] T030 [US5] Add robust section-level validation to `app/src/main/java/com/toyph/app/ToyConfig.kt`: verify each section is independently optional (missing → empty list default), log warnings for unrecognized top-level keys, handle malformed section arrays gracefully (log + skip bad entries), ensure old format (version absent or < 1.0.0) produces the specific error message from contracts/toy-config-v1.md.
- [x] T031 [US5] Add edge case handling to `app/src/main/java/com/toyph/app/pipeline/RenderingPipeline.kt` and `app/src/main/java/com/toyph/app/GlyphToyService.kt`: empty sequence with effects → black frame + effects; empty everything → static black frame; single sequence item → display indefinitely.

**Checkpoint**: Any combination of present/absent config sections works correctly without errors.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup and final validation

- [x] T032 Delete old `app/src/main/java/com/toyph/app/animations/` package entirely (GlyphAnimation.kt, ScanlineAnimation.kt, TripleScanlineAnimation.kt, PhosphorTrailAnimation.kt, GlitchTearAnimation.kt, CrtShimmerAnimation.kt, ScrollingTextAnimation.kt, CrossFadeTransition.kt) after confirming all logic is migrated and no imports reference it.
- [x] T033 Verify build compiles without warnings: `./gradlew assembleDebug`
- [ ] T034 On-device visual verification (MANUAL) per quickstart.md: sequence timing, effect overlay continuity, transition smoothness, filter application consistency.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Foundational
- **US2 (Phase 4)**: Depends on Foundational — can run in PARALLEL with US1
- **US3 (Phase 5)**: Depends on US1 (modifies SequencePlayer)
- **US4 (Phase 6)**: Depends on Foundational — can run in PARALLEL with US1 and US2
- **US5 (Phase 7)**: Depends on Foundational — can run after any story
- **Polish (Phase 8)**: Depends on ALL user stories being complete

### User Story Dependencies

```
Foundational (Phase 2)
    ├──▶ US1 (Sequence) ──▶ US3 (Transitions)
    ├──▶ US2 (Effects)      [parallel with US1]
    ├──▶ US4 (Filters)      [parallel with US1, US2]
    └──▶ US5 (Config)       [parallel, or after all]
                                    │
                              Polish (Phase 8)
```

### Within Each User Story

- Interfaces before implementations
- Individual component implementations (marked [P]) before orchestrators
- Orchestrators before wiring into pipeline
- Story complete before checkpoint validation

### Parallel Opportunities

- **Phase 1**: T003, T004, T005, T006 can all run in parallel (different files)
- **Phase 3+4+6**: US1, US2, US4 can all run in parallel after Foundational
- **Within US2**: T015-T019 (all 5 effect migrations) can run in parallel
- **Within US3**: T022, T023 (FadeTransition, NoneTransition) can run in parallel
- **Within US4**: T025, T026, T027 (all 3 filters) can run in parallel

---

## Parallel Example: User Story 2

```text
# After Foundational phase complete, launch all effect migrations in parallel:
T015: ScanlineEffect.kt
T016: TripleScanlineEffect.kt
T017: PhosphorTrailEffect.kt
T018: GlitchTearEffect.kt
T019: CrtShimmerEffect.kt

# Then sequentially:
T020: EffectCycler.kt (depends on effect implementations)
T021: Wire into pipeline (depends on EffectCycler)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (Sequence)
4. **STOP and VALIDATE**: Deploy to device, verify glyph/animation sequence plays correctly
5. At this point the app works with the new architecture — no effects, no filters, no transitions, but content displays correctly

### Incremental Delivery

1. Setup + Foundational → Architecture in place
2. Add US1 (Sequence) → Glyphs and scrolling text play in order (MVP)
3. Add US2 (Effects) → Visual overlays cycle independently
4. Add US3 (Transitions) → Smooth handoffs between sequence items
5. Add US4 (Filters) → Post-processing modifies final output
6. Add US5 (Config) → Robust validation and error handling
7. Polish → Delete old code, verify build, on-device testing

### Parallel Strategy

After Foundational phase:
- Stream A: US1 → US3 (sequence then transitions)
- Stream B: US2 (effects — independent)
- Stream C: US4 (filters — independent)
- Then: US5 + Polish

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in same phase
- [Story] label maps task to specific user story for traceability
- Each user story checkpoint should be independently deployable and testable on device
- Commit after each task or logical group
- The old `animations/` package is kept as reference during development and deleted in Polish (T032)
- Constitution Principle IV amendment (adding Effect/Filter/Transition interfaces) should be proposed after merge
