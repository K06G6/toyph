# Tasks: Marquee Text Animation

**Input**: Design documents from `/specs/marquee/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: No test tasks included (not requested in specification).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: No new project structure needed — existing Android project with established conventions.

- [x] T001 Create `app/src/main/assets/fonts/` directory for bundled bitmap font assets

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend configuration parsing to support SCROLLING_TEXT animation type. MUST complete before any user story.

**CRITICAL**: No user story work can begin until this phase is complete.

- [x] T002 Extend `AnimationConfig` data class with optional `text: String?` and `font: String?` fields, and update `ToyConfig.parse()` to extract them from JSON in `app/src/main/java/com/toyph/app/ToyConfig.kt`

**Checkpoint**: Configuration can parse SCROLLING_TEXT entries — user story implementation can now begin.

---

## Phase 3: User Story 1 - Display Scrolling Text on AOD (Priority: P1) MVP

**Goal**: A configured text message scrolls right-to-left across the LED grid, bookended by fade-to-black and fade-from-black transitions, integrating seamlessly with existing animations.

**Independent Test**: Configure a SCROLLING_TEXT animation in `toy-config.json` with a short test message and a minimal hand-crafted font. On AOD activation, verify: image fades to black → text scrolls right-to-left → screen fades back to image → other animations resume.

### Implementation for User Story 1

- [x] T003 [P] [US1] Implement `TextBitmapRenderer` class in `app/src/main/java/com/toyph/app/animations/TextBitmapRenderer.kt` — load bitmap font JSON from assets via `org.json`, implement `renderText(text: String): Pair<IntArray, Int>` that builds the padded wide matrix (13 rows × (GRID_SIZE + textWidth + GRID_SIZE) columns) with top/bottom margins, 1px inter-character spacing, and blank-space substitution (width 5) for missing characters
- [x] T004 [P] [US1] Implement `ScrollingTextAnimation` class in `app/src/main/java/com/toyph/app/animations/ScrollingTextAnimation.kt` — implements `GlyphAnimation` interface, takes the pre-computed text matrix IntArray and its width, `frameCount = textWidth + GRID_SIZE + 1`, `generateFrame(tick)` extracts 13×13 viewport at column offset `tick`, `baseFrame()` returns all-black IntArray(169)
- [x] T005 [P] [US1] Create minimal hand-crafted test font JSON in `app/src/main/assets/fonts/default.json` with at least uppercase A-Z, space, and digits 0-9 at charHeight=11, topMargin=1, bottomMargin=1, defaultSpacing=1 — follow schema in `specs/marquee/contracts/font-schema.json`
- [x] T006 [US1] Modify `GlyphToyService.kt` to support SCROLLING_TEXT: add `"SCROLLING_TEXT"` case to `createAnimation()` that loads font via `TextBitmapRenderer`, renders text to matrix, and returns `ScrollingTextAnimation`; in the animation loop, detect SCROLLING_TEXT type and play `CrossFadeTransition(currentImage, blackFrame)` before and `CrossFadeTransition(blackFrame, currentImage)` after the scroll animation in `app/src/main/java/com/toyph/app/GlyphToyService.kt`
- [x] T007 [US1] Add a `SCROLLING_TEXT` animation entry to `app/src/main/assets/toy-config.json` with test text (e.g., "THIS IS THE WAY"), font path `fonts/default.json`, frameIntervalMs 80, trailingIntervalMs 1000
- [x] T008 [US1] Build with `./gradlew installDebug` and verify on physical device: image fades to black, text scrolls right-to-left smoothly, screen fades back to original image, subsequent animations play normally

**Checkpoint**: User Story 1 is fully functional — marquee text scrolls on AOD with fade transitions. MVP complete.

---

## Phase 4: User Story 2 - Custom Bitmap Fonts (Priority: P2)

**Goal**: Robust font loading with validation, graceful error handling for missing/invalid fonts, and support for multiple fonts across different SCROLLING_TEXT animations.

**Independent Test**: Configure two SCROLLING_TEXT animations referencing different font files. Verify each renders with its own font. Test with a missing font path and verify the animation is skipped with a warning. Test with empty text and verify the animation is skipped.

### Implementation for User Story 2

- [x] T009 [P] [US2] Add font validation in `TextBitmapRenderer` in `app/src/main/java/com/toyph/app/animations/TextBitmapRenderer.kt`: verify `charHeight + topMargin + bottomMargin == GRID_SIZE`, verify each glyph pixel array length equals `charHeight * width`, verify all pixel values are 0 or MAX_BRIGHTNESS — throw descriptive exceptions on failure
- [x] T010 [P] [US2] Add error handling for missing or invalid font files in `GlyphToyService.kt`: catch exceptions from font loading/rendering, log a warning, and skip the SCROLLING_TEXT animation (continue with remaining animations) in `app/src/main/java/com/toyph/app/GlyphToyService.kt`
- [x] T011 [P] [US2] Add empty text handling in `GlyphToyService.kt`: when SCROLLING_TEXT config has empty or blank text, skip the animation entirely (no fade-to-black, no scroll) in `app/src/main/java/com/toyph/app/GlyphToyService.kt`
- [ ] T012 [US2] Create a second bitmap font JSON in `app/src/main/assets/fonts/` (hand-crafted, visually distinct from default) and configure two SCROLLING_TEXT animations with different fonts in `app/src/main/assets/toy-config.json`
- [ ] T013 [US2] Build and verify on physical device: both fonts render correctly, missing font logs warning and skips, empty text skips cleanly

**Checkpoint**: Font system is robust — multiple fonts, validation, and graceful error handling all working.

---

## Phase 5: User Story 3 - Font Converter Tool (Priority: P3)

**Goal**: A Python script converts any TTF/OTF font to the project's bitmap font JSON format, with documentation for usage.

**Independent Test**: Run the converter on a freely-available pixel font TTF. Verify output is valid JSON matching the font schema. Replace the hand-crafted test font with the converter output and verify scrolling text renders correctly on device.

### Implementation for User Story 3

- [x] T014 [P] [US3] Create `tools/font-to-glyph.py` Python converter script using Pillow: accepts `--input` (TTF/OTF path), `--output` (JSON output path), `--name` (font name), `--char-height` (default 11), `--top-margin` (default 1), `--bottom-margin` (default 1), `--spacing` (default 1), `--charset` (default ASCII 32-126, or path to file of characters), `--threshold` (default 128 for binary conversion); renders each character, thresholds to binary, outputs JSON per `specs/marquee/contracts/font-schema.json`
- [x] T015 [P] [US3] Create `tools/README.md` documenting: prerequisites (Python 3, Pillow install via `pip install Pillow`), usage examples (basic conversion, custom character set, custom height/margins), output format description, and how to deploy the generated font into the project assets
- [ ] T016 [US3] Run converter on a suitable open-source pixel font to generate production `app/src/main/assets/fonts/default.json` replacing the hand-crafted version
- [ ] T017 [US3] Build and verify on physical device with converter-generated font: text is legible, characters are well-formed at 11px height

**Checkpoint**: Complete toolchain — fonts can be converted from TTF/OTF and used in scrolling text animations.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final cleanup and validation across all stories.

- [ ] T018 Finalize `app/src/main/assets/toy-config.json` with production animation configuration (appropriate text, font, timing)
- [ ] T019 Run quickstart.md validation — verify all steps in `specs/marquee/quickstart.md` work end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Foundational (Phase 2)
- **US2 (Phase 4)**: Depends on US1 (Phase 3) — extends TextBitmapRenderer and GlyphToyService from US1
- **US3 (Phase 5)**: Depends on Foundational (Phase 2) only — converter is standalone, but uses font schema defined during US1
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — no dependencies on other stories
- **User Story 2 (P2)**: Depends on US1 (extends the font loading and service code written in US1)
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) — Python tool is independent of Android code, but T016-T017 depend on US1 being complete (needs the animation to test the font)

### Within Each User Story

- Models/renderers before animation classes
- Animation classes before service integration
- Service integration before config updates
- Config updates before device testing
- Device testing is always the final task in a story

### Parallel Opportunities

- T003, T004, T005 can all run in parallel (different files: TextBitmapRenderer, ScrollingTextAnimation, font JSON)
- T009, T010, T011 can all run in parallel (different concerns in different files)
- T014, T015 can run in parallel (Python script and README)
- US3 Phase 5 (T014-T015) can start in parallel with US1 Phase 3 (converter doesn't depend on Android code)

---

## Parallel Example: User Story 1

```text
# Launch T003, T004, T005 in parallel (different files, no dependencies):
Task: "Implement TextBitmapRenderer in app/.../TextBitmapRenderer.kt"
Task: "Implement ScrollingTextAnimation in app/.../ScrollingTextAnimation.kt"
Task: "Create minimal test font JSON in app/.../fonts/default.json"

# Then sequentially:
Task: "Modify GlyphToyService.kt" (depends on T003, T004)
Task: "Add SCROLLING_TEXT to toy-config.json" (depends on T005, T006)
Task: "Build and verify on device" (depends on all above)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (create fonts directory)
2. Complete Phase 2: Foundational (extend ToyConfig)
3. Complete Phase 3: User Story 1 (core scrolling animation)
4. **STOP and VALIDATE**: Test on physical device — text scrolls with fade transitions
5. Deploy if ready — MVP delivers the core marquee experience

### Incremental Delivery

1. Complete Setup + Foundational → Config ready
2. Add User Story 1 → Test on device → MVP! (scrolling text works)
3. Add User Story 2 → Test on device → Robust font system (validation, error handling, multiple fonts)
4. Add User Story 3 → Test on device → Complete toolchain (font converter + documentation)
5. Each story adds value without breaking previous stories

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All device testing requires a physical Nothing Phone 4a Pro — emulators do not support GlyphMatrix SDK
