# Tasks: Multi-Image Animation Sequencing

**Input**: Design documents from `/specs/001-multi-image-animation/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Tests**: Not requested. On-device visual verification only.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the configuration asset and data model that all user stories depend on

- [x] T001 Create default toy configuration asset in app/src/main/assets/glyphs/toy-config.json with version, animationIntervalMs, crossFadeDurationMs, crossFadeFrameIntervalMs, images list (single default.json), and animations list (single TRIPLE_SCANLINE)
- [x] T002 Create ToyConfig data model and JSON parser in app/src/main/java/com/toyph/app/ToyConfig.kt with ToyConfig and AnimationConfig data classes, a parse(json: String): ToyConfig function using org.json, and validation (frameIntervals >= 30, negative intervals clamped to 0)

---

## Phase 2: Foundational - US5 (Remove cycleDuration from Animation Interface) (Priority: P1)

**Purpose**: Decouple animation timing from the animation interface. MUST complete before any sequencer work.

**Goal**: Animations define only frame-level behavior; timing is owned by configuration.

**Independent Test**: Build compiles. Existing single-animation behavior still works with a hardcoded animation on a single image.

- [x] T003 [US5] Remove cycleDurationMs from GlyphAnimation interface in app/src/main/java/com/toyph/app/GlyphAnimation.kt
- [x] T004 [P] [US5] Remove cycleDurationMs constructor parameter and field from ScanlineAnimation in app/src/main/java/com/toyph/app/ScanlineAnimation.kt
- [x] T005 [P] [US5] Remove cycleDurationMs constructor parameter and field from TripleScanlineAnimation in app/src/main/java/com/toyph/app/TripleScanlineAnimation.kt
- [x] T006 [US5] Update GlyphToyService.startAnimation() in app/src/main/java/com/toyph/app/GlyphToyService.kt to remove the cycleDurationMs rest calculation (lines 70-71) and replace with a simple delay using animationIntervalMs from a temporary hardcoded value (will be replaced by config in Phase 3)

**Checkpoint**: Build compiles. Single animation plays on single image with fixed interval rest. cycleDurationMs is fully removed from the codebase.

---

## Phase 3: US2 + US3 - Global Animation List with Configurable Intervals (Priority: P1) MVP

**Goal**: The service loads a toy configuration and runs a global list of animations sequentially on the current image, with a configurable pause between each animation.

**Independent Test**: Configure toy-config.json with two animations (SCANLINE and TRIPLE_SCANLINE) and a 2-second interval. Verify on device: scanline plays, 2-second pause showing base image, triple-scanline plays, then the cycle repeats.

- [x] T007 [US2] Rewrite GlyphToyService.onBind() in app/src/main/java/com/toyph/app/GlyphToyService.kt to load and parse toy-config.json from assets using ToyConfig.parse(), replacing the hardcoded glyphFile and defaultAnimation fields
- [x] T008 [US2] Add animation factory function to GlyphToyService in app/src/main/java/com/toyph/app/GlyphToyService.kt that maps AnimationConfig.type string to GlyphAnimation instances (SCANLINE -> ScanlineAnimation, TRIPLE_SCANLINE -> TripleScanlineAnimation), accepting an IntArray image parameter
- [x] T009 [US2] [US3] Rewrite GlyphToyService.startAnimation() in app/src/main/java/com/toyph/app/GlyphToyService.kt with the sequencer inner loop: iterate over config.animations list, for each animation play all frames (tick 0..frameCount-1), then if not last animation delay for config.animationIntervalMs showing the base image
- [x] T010 [US3] Update toy-config.json in app/src/main/assets/glyphs/toy-config.json with two animations (SCANLINE and TRIPLE_SCANLINE) and animationIntervalMs of 2000 for testing

**Checkpoint**: Two animations play sequentially on a single image with a configurable pause between them. This is the MVP.

---

## Phase 4: US1 - Multiple Image Cycling (Priority: P1)

**Goal**: The service cycles through multiple glyph images, running the full global animation list on each before moving to the next. Hard cut between images (cross-fade added in Phase 5).

**Independent Test**: Configure toy-config.json with two image paths. Verify on device: all animations play on image 1, then image 2 appears (hard cut), all animations play on image 2, then loops back to image 1.

- [x] T011 [P] [US1] Create a second glyph pattern JSON file in app/src/main/assets/glyphs/pattern2.json with a distinct brightness pattern (e.g., inverted or shifted version of default.json) for visual testing
- [x] T012 [US1] Add multi-image loading to GlyphToyService.onBind() in app/src/main/java/com/toyph/app/GlyphToyService.kt: load each path in config.images via loadGlyph(), collect into a List<IntArray>, skip images that fail to load with a log warning
- [x] T013 [US1] Wrap the sequencer loop from T009 in an outer image-cycling loop in GlyphToyService.startAnimation() in app/src/main/java/com/toyph/app/GlyphToyService.kt: for each image in the list, set it as the current base image, run the full animation list, then advance to the next image (wrapping to index 0 after the last)
- [x] T014 [US1] Update toy-config.json in app/src/main/assets/glyphs/toy-config.json to include both glyphs/default.json and glyphs/pattern2.json in the images list

**Checkpoint**: Multiple images cycle with all animations playing on each. Transition between images is a hard cut. Single-image config still works (animations repeat on the same image).

---

## Phase 5: US4 - Cross-Fade Transition Between Images (Priority: P2)

**Goal**: When transitioning from one image to the next, the old image fades out and the new image fades in over 1000ms using linear brightness interpolation.

**Independent Test**: Configure two images with distinct patterns. Verify on device: after animations complete on image 1, a smooth 1-second blend occurs where image 1 dims and image 2 brightens simultaneously, then animations begin on image 2.

- [x] T015 [P] [US4] Create CrossFadeTransition implementing GlyphAnimation in app/src/main/java/com/toyph/app/CrossFadeTransition.kt with constructor(sourceImage: IntArray, targetImage: IntArray, durationMs: Long, frameIntervalMs: Long), frameCount = (durationMs / frameIntervalMs).toInt(), generateFrame(tick) computing integer linear interpolation: result[i] = (source[i] * (frameCount - tick) + target[i] * tick) / frameCount clamped to 0-4095, baseFrame() returning targetImage.copyOf()
- [x] T016 [US4] Integrate cross-fade into the image-cycling loop in GlyphToyService.startAnimation() in app/src/main/java/com/toyph/app/GlyphToyService.kt: after the last animation on the current image, if there are multiple images, create a CrossFadeTransition from current image to next image using config.crossFadeDurationMs and config.crossFadeFrameIntervalMs, and play it as a regular animation (tick 0..frameCount-1)
- [x] T017 [US4] Handle single-image case in GlyphToyService.startAnimation() in app/src/main/java/com/toyph/app/GlyphToyService.kt: when only one image is configured, skip cross-fade and restart the animation list on the same image

**Checkpoint**: Cross-fade transitions work smoothly between images. Single-image config shows no fade. Full sequencer loop is complete.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Edge cases, error handling, and build verification

- [x] T018 [P] Handle empty image list in GlyphToyService.startAnimation() in app/src/main/java/com/toyph/app/GlyphToyService.kt: if no images loaded successfully, display a blank 169-element zero IntArray via setMatrixFrame and return without starting the animation loop
- [x] T019 [P] Handle empty animation list in GlyphToyService.startAnimation() in app/src/main/java/com/toyph/app/GlyphToyService.kt: if config.animations is empty, display each image as a static frame for animationIntervalMs before transitioning to the next
- [x] T020 [P] Update constitution in .specify/memory/constitution.md: amend Battery Discipline principle II to reference config-level animationIntervalMs instead of animation-level cycleDurationMs, and update Animation as Interface principle IV development workflow step 4 to reflect new wiring approach
- [x] T021 Verify build compiles without warnings by running ./gradlew installDebug and confirm the full sequencer loop on device with the default toy-config.json

---

## Phase 7: New Animation Types — CRT Shimmer, Phosphor Trail, Glitch Tear (Extension of US2)

**Goal**: Add three new `GlyphAnimation` implementations that plug into the existing animation framework. Each implements the `GlyphAnimation` interface with `(baseImage: IntArray, frameIntervalMs: Long)` constructor, and is registered in the `createAnimation()` factory.

**Independent Test**: Configure `toy-config.json` with all three new animation types. Verify on device: CRT Shimmer shows alternating-row brightness breathing, Phosphor Trail shows a top-to-bottom sweep with fading echo, Glitch Tear shows brief horizontal band displacement. Each animation plays, pauses for its trailing interval, then the next animation begins.

### Implementation

- [x] T022 [P] [US2] Create CrtShimmerAnimation implementing GlyphAnimation in app/src/main/java/com/toyph/app/animations/CrtShimmerAnimation.kt — constructor(baseImage: IntArray, frameIntervalMs: Long = 67L), frameCount = 24, AMPLITUDE = 0.15. For each tick: compute phase = 2*PI*tick/frameCount, evenFactor = 1.0 + AMPLITUDE*sin(phase), oddFactor = 1.0 - AMPLITUDE*sin(phase). Copy baseImage, then for each pixel: row = i/GRID_SIZE, apply evenFactor if row%2==0 else oddFactor, multiply base brightness, coerceIn(0, MAX_BRIGHTNESS). baseFrame() returns baseImage.copyOf()
- [x] T023 [P] [US2] Create PhosphorTrailAnimation implementing GlyphAnimation in app/src/main/java/com/toyph/app/animations/PhosphorTrailAnimation.kt — constructor(baseImage: IntArray, frameIntervalMs: Long = 50L), frameCount = GRID_SIZE + 2 (15). Constants: SWEEP_BOOST=1.5, TRAIL1_BOOST=1.25, TRAIL2_BOOST=1.10. For each tick: sweepRow = tick, copy baseImage, for each pixel: row = i/GRID_SIZE, distance = sweepRow - row, boost = 1.5 if distance==0, 1.25 if distance==1, 1.10 if distance==2, else 1.0. Multiply base brightness by boost, coerceIn(0, MAX_BRIGHTNESS). baseFrame() returns baseImage.copyOf()
- [x] T024 [P] [US2] Create GlitchTearAnimation implementing GlyphAnimation in app/src/main/java/com/toyph/app/animations/GlitchTearAnimation.kt — constructor(baseImage: IntArray, frameIntervalMs: Long = 80L), frameCount = 6. Constants: BAND_START=4, BAND_END=8. For ticks 0-3: return baseImage.copyOf(). For tick 4: copy baseImage, for rows 4..8 shift right 1px using System.arraycopy(frame, offset, frame, offset+1, GRID_SIZE-1) and zero-fill frame[offset]. For tick 5: copy baseImage, for rows 4..8 shift left 1px using System.arraycopy(frame, offset+1, frame, offset, GRID_SIZE-1) and zero-fill frame[offset+GRID_SIZE-1]. baseFrame() returns baseImage.copyOf()
- [x] T025 [US2] Register three new animation types in createAnimation() factory in app/src/main/java/com/toyph/app/GlyphToyService.kt — add cases: "CRT_SHIMMER" -> CrtShimmerAnimation(image, animConfig.frameIntervalMs), "PHOSPHOR_TRAIL" -> PhosphorTrailAnimation(image, animConfig.frameIntervalMs), "GLITCH_TEAR" -> GlitchTearAnimation(image, animConfig.frameIntervalMs). Add corresponding imports for all three classes
- [x] T026 [P] [US2] Update toy-config.json in app/src/main/assets/toy-config.json — replace existing animations array with: CRT_SHIMMER (67ms, 3000ms trailing), PHOSPHOR_TRAIL (50ms, 4000ms trailing), GLITCH_TEAR (80ms, 5000ms trailing)
- [x] T027 [US2] Verify build compiles without warnings by running ./gradlew assembleDebug

**Checkpoint**: All three new animations play on device. Full sequence: CRT Shimmer breathes, Phosphor Trail sweeps, Glitch Tear jitters, each with trailing pauses, then cross-fades to next image and repeats.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - start immediately ✅ Complete
- **Foundational US5 (Phase 2)**: Depends on Phase 1 ✅ Complete
- **US2+US3 (Phase 3)**: Depends on Phase 2 ✅ Complete
- **US1 (Phase 4)**: Depends on Phase 3 ✅ Complete
- **US4 (Phase 5)**: Depends on Phase 4 ✅ Complete
- **Polish (Phase 6)**: Depends on Phase 5 ✅ Complete
- **New Animations (Phase 7)**: Depends on Phase 6 (framework must be complete). T022, T023, T024 are fully parallel. T025 depends on T022-T024 (needs classes to import). T026 is parallel with T025 (different file). T027 depends on T025+T026

### User Story Dependencies

- **US5 (P1)**: Foundational - blocks all other stories
- **US2+US3 (P1)**: Depends on US5. Delivers the animation sequencer on a single image (MVP)
- **US1 (P1)**: Depends on US2+US3. Adds multi-image cycling
- **US4 (P2)**: Depends on US1. Adds smooth transitions between images

### Within Each Phase

- Tasks marked [P] within the same phase can run in parallel
- Non-[P] tasks run sequentially in order listed

### Parallel Opportunities

- **Phase 1**: T001 and T002 are independent and can run in parallel ✅
- **Phase 2**: T004 and T005 can run in parallel after T003 ✅
- **Phase 4**: T011 can run in parallel with T012-T013 ✅
- **Phase 5**: T015 can run in parallel with other work until T016 ✅
- **Phase 6**: T018, T019, T020 can all run in parallel ✅
- **Phase 7**: T022, T023, T024 are fully parallel (3 independent animation files). T025 and T026 are parallel with each other (different files) after animation classes are written

---

## Parallel Example: Phase 7

```
# Launch all 3 animation classes in parallel:
Task T022: "Create CrtShimmerAnimation in animations/CrtShimmerAnimation.kt"
Task T023: "Create PhosphorTrailAnimation in animations/PhosphorTrailAnimation.kt"
Task T024: "Create GlitchTearAnimation in animations/GlitchTearAnimation.kt"

# Then launch factory + config in parallel:
Task T025: "Register types in createAnimation() factory in GlyphToyService.kt"
Task T026: "Update toy-config.json with new animation entries"

# Finally:
Task T027: "Verify build compiles"
```

---

## Implementation Strategy

### Current State

Phases 1-6 are complete (T001-T021). The framework is fully operational with SCANLINE and TRIPLE_SCANLINE animations, multi-image cycling, cross-fade transitions, and edge case handling.

### Phase 7: New Animations

1. Implement T022, T023, T024 in parallel (3 animation classes)
2. Implement T025 + T026 in parallel (factory registration + config update)
3. Run T027 (build verification)
4. **VALIDATE on device**: All 5 animation types play correctly in sequence

---

## Notes

- No test tasks generated (on-device visual verification only, per plan.md)
- US2 and US3 are combined in Phase 3 because they're both part of the same sequencer inner loop rewrite and cannot be meaningfully separated
- US5 is placed as foundational (Phase 2) because the interface change blocks all animation-related work
- Each checkpoint describes what to verify on a physical Nothing Phone (4a) Pro
- Commit after each task or logical group per Constitution development workflow
- Phase 7 tasks (T022-T027) extend the animation library without changing the framework
- All 3 animation classes follow the same constructor pattern: (baseImage: IntArray, frameIntervalMs: Long)
- Per-frame allocation is limited to a single IntArray.copyOf() in all new animations (Battery Discipline)
