# Feature Specification: Multi-Image Animation Sequencing

**Feature Branch**: `001-multi-image-animation`  
**Created**: 2026-04-07  
**Status**: Draft  
**Input**: User description: "Support multiple images and multiple animations with configurable sequencing, cross-fade transitions between images, and interval-based animation scheduling."

## Clarifications

### Session 2026-04-07

- Q: Are animations assigned globally (same list for all images) or per-image? → A: Global -- one animation list runs on every image identically.
- Q: What is the cross-fade duration between images? → A: 1000ms (smooth, clearly visible blend).

### Session 2026-04-08

- Q: Is the animation interval uniform (single value for all animations) or per-animation? → A: Per-animation -- each animation entry has its own `trailingIntervalMs`.
- Q: Should CRT Shimmer play multiple breath cycles or one? → A: Single cycle (24 frames, ~1.6s) -- trailing interval controls pacing.
- Q: Should new animation types (CRT Shimmer, Phosphor Trail, Glitch Tear) be added as spec-level requirements? → A: No -- covered by FR-002 and Constitution IV; individual animations are implementation details.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure Multiple Images (Priority: P1)

As a glyph toy creator, I want to provide a list of glyph images so that my toy cycles through different patterns on the LED grid.

**Why this priority**: Without multiple image support, the system cannot display more than one pattern. This is the foundational change that all other stories depend on.

**Independent Test**: Can be fully tested by loading two or more glyph JSON files and verifying the service cycles through each image in order.

**Acceptance Scenarios**:

1. **Given** a configuration with three glyph images, **When** the glyph toy service starts, **Then** the first image is displayed initially.
2. **Given** the service is running with multiple images, **When** all animations for the current image complete, **Then** the display transitions to the next image.
3. **Given** the service is displaying the last image in the list, **When** all animations for that image complete, **Then** the display loops back to the first image.
4. **Given** a configuration with a single image, **When** the service runs, **Then** it behaves identically to the current single-image behavior (animations repeat on the same image indefinitely).

---

### User Story 2 - Configure a Global Animation List (Priority: P1)

As a glyph toy creator, I want to define a single list of animations that runs on every image so that I can create complex visual sequences without writing new animation code.

**Why this priority**: This is the core sequencing feature. Every image runs through the full global animation list before transitioning.

**Independent Test**: Can be fully tested by configuring two animations (e.g., scanline then triple-scanline) and verifying they play sequentially on each image.

**Acceptance Scenarios**:

1. **Given** a global animation list with two animations, **When** the first animation completes all its frames on the current image, **Then** the second animation begins after the configured interval.
2. **Given** a global animation list with three animations, **When** the service is running on any image, **Then** animations play in the configured order: first, then second, then third.
3. **Given** the last animation in the global list finishes on the current image, **When** the current image is not the last in the list, **Then** a cross-fade transition begins to the next image.

---

### User Story 3 - Configurable Interval Between Animations (Priority: P1)

As a glyph toy creator, I want to specify a pause interval between animations so that I can control the pacing of my animation sequence.

**Why this priority**: The interval replaces the existing `cycleDuration` concept and is essential for controlling the visual rhythm of animation sequences.

**Independent Test**: Can be fully tested by setting different interval values and measuring the pause between consecutive animations on the same image.

**Acceptance Scenarios**:

1. **Given** an animation with `trailingIntervalMs` of 2000, **When** that animation finishes and the next begins, **Then** the base image is displayed for approximately 2 seconds during the pause.
2. **Given** an animation with `trailingIntervalMs` of 0, **When** that animation finishes, **Then** the next animation begins immediately with no visible pause.

---

### User Story 4 - Cross-Fade Transition Between Images (Priority: P2)

As a glyph toy creator, I want the old image to fade out and the new image to fade in when transitioning between images so that the visual effect feels smooth rather than jarring.

**Why this priority**: Cross-fade enhances visual quality but the system is functional without it (a hard cut is acceptable as a fallback).

**Independent Test**: Can be fully tested by configuring two images and observing the LED brightness values during the transition. The old image brightness decreases while the new image brightness increases.

**Acceptance Scenarios**:

1. **Given** two images in the sequence, **When** the transition between them occurs, **Then** the old image's LED brightness values gradually decrease to zero while the new image's values gradually increase from zero to their target brightness over 1000ms.
2. **Given** a cross-fade in progress, **When** 1000ms have elapsed, **Then** the new image is displayed at its full configured brightness.
3. **Given** only one image in the list, **When** animations finish, **Then** no cross-fade occurs and animations simply repeat on the same image.

---

### User Story 5 - Remove cycleDuration from Animation Interface (Priority: P1)

As a glyph toy creator, I want the animation interface to only define frame-level behavior (frame interval and frame generation) so that timing and sequencing are controlled externally through configuration.

**Why this priority**: This is a breaking interface change that decouples animation logic from scheduling, enabling reusable animations across different timing configurations.

**Independent Test**: Can be fully tested by verifying that existing animations (scanline, triple-scanline) work correctly after removing `cycleDuration` and that the service drives timing externally.

**Acceptance Scenarios**:

1. **Given** an animation implementation, **When** it is instantiated, **Then** it does not require or expose a `cycleDuration` property.
2. **Given** a configured animation with `frameIntervalMs` and `frameCount`, **When** the service runs it, **Then** the animation plays for exactly `frameCount * frameIntervalMs` milliseconds.

---

### Edge Cases

- What happens when the image list is empty? The service should not start animations and should display a blank (all-zero) grid.
- What happens when an image has no animations configured? The image is displayed as a static frame for 2000ms, then transitions to the next image.
- What happens when a glyph JSON file cannot be loaded? The service skips that image and proceeds to the next one. If no images can be loaded, the service displays a blank grid.
- What happens when the animation interval is negative? The system treats it as zero (no pause).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support a configurable list of glyph images, loaded in order from the configuration.
- **FR-002**: System MUST support a single global list of animations that is applied to every image in sequence.
- **FR-003**: System MUST play all configured animations on the current image before transitioning to the next image.
- **FR-004**: System MUST support a configurable per-animation trailing interval (`trailingIntervalMs`, in milliseconds) between consecutive animations on the same image. During this interval, the base image is displayed.
- **FR-005**: System MUST perform a 1000ms cross-fade transition when changing from one image to another, where the old image fades out and the new image fades in simultaneously using linear brightness interpolation.
- **FR-006**: System MUST loop back to the first image after the last image's animations complete.
- **FR-007**: The `GlyphAnimation` interface MUST no longer include `cycleDurationMs`. Animations define only `frameIntervalMs`, `frameCount`, and frame generation.
- **FR-008**: System MUST gracefully handle an empty image list by displaying a blank grid.
- **FR-009**: System MUST skip images whose glyph data fails to load and continue with the remaining images.
- **FR-010**: System MUST display an image as a static frame when it has no animations configured, holding for 2000ms before transitioning.

### Key Entities

- **Glyph Image**: A brightness map (IntArray of 169 values) representing one LED pattern. Loaded from a JSON asset file.
- **Animation**: A visual effect applied to a glyph image, defined by frame interval, frame count, and a frame generation function. No longer owns cycle timing.
- **Animation List**: A single global ordered list of animations. The entire list runs on each image before the image transitions. Animations are separated by a configurable pause.
- **Image Sequence**: An ordered list of glyph images. The system cycles through them with 1000ms cross-fade transitions. Each image receives the full global animation list.
- **Toy Configuration**: The top-level configuration combining the image sequence, animation assignments, interval timing, and cross-fade settings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure and display at least 5 different glyph images in a single toy sequence.
- **SC-002**: Users can configure a global animation list with at least 3 different animations.
- **SC-003**: Transitions between images are visually smooth, with the cross-fade completing without visible flickering or frame drops.
- **SC-004**: Each animation's trailing interval pause is accurate to within 100ms of its configured `trailingIntervalMs` value.
- **SC-005**: The system completes a full cycle through all images and animations and loops back to the beginning without interruption.
- **SC-006**: Existing single-image, single-animation configurations continue to work without modification (backward compatibility for the toy configuration, not the animation interface).

## Assumptions

- The cross-fade transition uses linear interpolation between brightness values of the old and new images over 1000ms.
- Configuration of images and animations will be done through a structured format within the project assets (e.g., a JSON configuration file). The exact configuration format will be defined during the planning phase.
- All glyph images target the same device (Phone 4a Pro) and use the same 13x13 grid.
- Each animation entry in the global list has its own `trailingIntervalMs` controlling the pause after that animation completes. This allows per-animation pacing control.
- Animations are stateless between cycles: each time an animation is applied to an image, it starts from tick 0.
