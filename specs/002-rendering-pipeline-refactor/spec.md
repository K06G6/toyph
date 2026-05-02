# Feature Specification: Layered Rendering Pipeline Refactor

**Feature Branch**: `002-rendering-pipeline-refactor`  
**Created**: 2026-04-09  
**Status**: Draft  
**Input**: User description: "Refactor the display system into five distinct layers: Glyphs, Animations, Effects, Filters, and Transitions, each with independent configuration and a defined order of application."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure a Mixed Glyph and Animation Sequence (Priority: P1)

A user configures a display sequence that interleaves static glyphs and animations. For example: Mandalorian glyph for 10 seconds, then a scrolling text animation, then the Rebel glyph for 8 seconds. Each item in the sequence has its own display duration and transition style. The system plays through the sequence in order, looping back to the beginning after the last item.

**Why this priority**: This is the core behavioral change — unifying glyphs and animations into a single ordered sequence. Without this, none of the other layers make sense.

**Independent Test**: Can be fully tested by defining a sequence of 2+ items (at least one glyph and one animation) in configuration and verifying they display in order with correct durations.

**Acceptance Scenarios**:

1. **Given** a config with sequence [Mandalorian (10s), ScrollingText, Rebel (8s)], **When** the service starts, **Then** Mandalorian displays for 10 seconds, scrolling text plays for its full animation length, Rebel displays for 8 seconds, and the sequence loops.
2. **Given** a config with a single glyph in the sequence, **When** the service starts, **Then** that glyph displays indefinitely at its configured brightness.
3. **Given** a config with only animations in the sequence, **When** the service starts, **Then** each animation plays for its full duration before the next begins.
4. **Given** a sequence item with a display duration of 0 or negative, **When** the config is parsed, **Then** the system uses a reasonable default display duration (e.g., 5 seconds).

---

### User Story 2 - Effects Run Independently of the Sequence (Priority: P1)

Effects (scanline, triple scanline, phosphor trail, glitch tear, CRT shimmer) run on their own schedule, overlaying whatever glyph or animation is currently displayed. They cycle through their own sequence with their own intervals, completely independent of which sequence item is showing. Effects modify the brightness values of the current frame without changing the underlying content.

**Why this priority**: This decouples the visual enhancement layer from content display, which is a fundamental architectural change required for the layered pipeline.

**Independent Test**: Can be tested by configuring a static glyph with one or more effects and verifying the effects cycle through on their own timing regardless of the glyph's display duration.

**Acceptance Scenarios**:

1. **Given** a glyph displaying and effects [SCANLINE (interval 10s), PHOSPHOR_TRAIL (interval 10s)] configured, **When** the glyph is being shown, **Then** effects cycle on their own schedule independently of the glyph's display duration.
2. **Given** effects configured and the sequence transitions from one glyph to another, **When** the transition occurs, **Then** effects continue running without interruption or reset.
3. **Given** no effects configured, **When** the sequence plays, **Then** items display without any effect overlay.
4. **Given** multiple effects configured, **When** effects play, **Then** they cycle through their own list in order with their own trailing intervals.

---

### User Story 3 - Transitions Between Sequence Items (Priority: P2)

Each glyph or animation in the sequence has a transition parameter that describes how it appears (how it replaces the previous item). The default transition is fade-out/fade-in. Other transition types can be added in the future. The transition is a property of the incoming sequence item.

**Why this priority**: Transitions provide visual polish and are a direct replacement for the current hardcoded cross-fade behavior. They must work before filters can be layered on top.

**Independent Test**: Can be tested by configuring two sequence items with different transition types and verifying each transition renders correctly.

**Acceptance Scenarios**:

1. **Given** a sequence [Glyph-A, Glyph-B with fade transition], **When** Glyph-A's duration expires, **Then** a fade-out/fade-in transition plays before Glyph-B appears.
2. **Given** a sequence item with no transition specified, **When** it becomes active, **Then** the default fade transition is used.
3. **Given** a sequence item with transition "none", **When** it becomes active, **Then** it replaces the previous item instantly with no transition animation.
4. **Given** a transition is playing, **When** effects are active, **Then** effects continue to apply on top of the transition frames.

---

### User Story 4 - Filters Apply to Final Output (Priority: P2)

Filters are static post-processing steps applied to every frame after all other layers (content + effects + transitions). Filters modify the final pixel values before they are sent to the display. Examples include variable brightness adjustment, blur, and dead pixel simulation.

**Why this priority**: Filters are the final stage of the pipeline. They add visual character but do not affect the core display logic.

**Independent Test**: Can be tested by configuring a static glyph with a filter (e.g., reduced brightness) and verifying the output pixel values are modified accordingly.

**Acceptance Scenarios**:

1. **Given** a brightness filter configured at 0.5, **When** any frame is rendered, **Then** all pixel brightness values are scaled to 50% of their computed values.
2. **Given** multiple filters configured, **When** a frame is rendered, **Then** filters are applied in the order they appear in configuration.
3. **Given** no filters configured, **When** frames are rendered, **Then** pixel values pass through unmodified.
4. **Given** a dead-pixel filter configured for specific coordinates, **When** frames are rendered, **Then** those pixel positions are always set to 0 brightness.

---

### User Story 5 - Separate Configuration Sections (Priority: P3)

The configuration file organizes each layer in its own section: sequence (glyphs and animations together), effects, filters. Each section is independently optional — omitting a section means that layer is inactive.

**Why this priority**: Clean configuration structure is important for usability but can be implemented alongside the other stories.

**Independent Test**: Can be tested by providing configs with various combinations of present/absent sections and verifying the system handles each correctly.

**Acceptance Scenarios**:

1. **Given** a config with only a sequence section (no effects, no filters), **When** the service starts, **Then** the sequence plays with no effects or filters applied.
2. **Given** a config with all sections populated, **When** the service starts, **Then** each layer operates as specified.
3. **Given** a config with effects but an empty sequence, **When** the service starts, **Then** a blank/black frame is displayed with effects running on top.
4. **Given** a config with an unrecognized section, **When** the config is parsed, **Then** the unknown section is ignored and a warning is logged.

---

### Edge Cases

- What happens when an animation in the sequence has zero frames (e.g., empty scrolling text)?  
  The item is skipped and the next sequence item begins immediately.
- What happens when all sequence items have zero duration?  
  The system falls back to displaying a black frame.
- What happens when an effect's frame interval is shorter than the display refresh rate?  
  The frame interval minimum (30ms) is enforced, same as current behavior.
- What happens during a transition when a filter modifies brightness to 0?  
  The transition still executes but produces no visible output — this is expected behavior since filters operate on the final output.
- What happens when the config version changes in a backward-incompatible way?  
  The system logs a version mismatch warning and attempts to parse with reasonable defaults.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support a unified sequence of display items where each item is either a static glyph or an animation (e.g., scrolling text).
- **FR-002**: Each glyph in the sequence MUST have a configurable display duration in milliseconds that determines how long it is shown, independent of effects or other timing.
- **FR-003**: Each animation in the sequence MUST use its total animation length as its display duration (i.e., the time to play all frames once).
- **FR-004**: The sequence MUST play items in the order they are configured, looping back to the first item after the last completes.
- **FR-005**: Effects (scanline, triple scanline, phosphor trail, glitch tear, CRT shimmer) MUST operate on their own independent cycle with their own sequence and timing intervals, regardless of which sequence item is currently displayed.
- **FR-006**: Effects MUST NOT reset or interrupt when the displayed sequence item changes.
- **FR-007**: Each sequence item MUST have an optional transition parameter that defines how it replaces the previous item, including an optional duration override.
- **FR-008**: The default transition MUST be fade-out/fade-in when no transition is explicitly specified. A global default transition duration MUST be configurable, and individual sequence items MAY override it.
- **FR-009**: Filters MUST be static processing steps that modify final pixel values after all other layers have been computed.
- **FR-010**: The rendering order MUST be: (1) Sequence content (glyph or animation frame) + transition blending, (2) effect overlay, (3) filter post-processing.
- **FR-011**: Each layer (sequence, effects, filters) MUST be configured in its own section of the configuration file.
- **FR-012**: Each configuration section MUST be independently optional — omitting a section disables that layer.
- **FR-013**: System MUST reject the old config format with a clear error message and provide documented upgrade instructions for converting to the new format.
- **FR-014**: Pixel brightness values MUST remain within the valid range (0 to 4095) after all layers are applied, clamping if necessary.

### Key Entities

- **Sequence Item**: An entry in the display sequence — either a glyph (static image with display duration) or an animation (dynamic content whose duration equals its animation length). Has an optional transition parameter.
- **Effect**: A visual overlay that modifies the brightness of the current frame. Has its own frame interval, frame count, and trailing interval. Cycles independently.
- **Filter**: A static post-processing operation applied to every final frame. Configured once and applied continuously.
- **Transition**: Defines how one sequence item visually replaces another. Applied during the handoff between two adjacent sequence items. Has a type (e.g., fade, none) and an optional duration that overrides the global default.
- **Rendering Pipeline**: The ordered processing chain: sequence content + transitions -> effects -> filters -> display output.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure a display sequence mixing static glyphs and animations in any order, and the system plays them sequentially with correct durations.
- **SC-002**: Effects visually overlay the displayed content and continue cycling uninterrupted across sequence item changes.
- **SC-003**: Transitions between sequence items render smoothly without visual glitches (no black flashes, no frame drops).
- **SC-004**: Filters consistently modify every output frame as configured, with no frames bypassing the filter stage.
- **SC-005**: A configuration with all five layers active (glyph, animation, effect, filter, transition) produces correct visual output with all layers composited in the defined order.
- **SC-006**: A configuration using only a subset of layers (e.g., just glyphs, no effects or filters) works correctly without errors.
- **SC-007**: When the old config format is loaded, the system produces a clear, actionable error message guiding the user to the new format. No silent failures or undefined behavior.

## Clarifications

### Session 2026-04-09

- Q: Do effects run one-at-a-time (cycling through their list) or simultaneously on independent timers? → A: Effects cycle one-at-a-time through their configured list, matching the current behavior pattern.
- Q: Should the old config format be auto-migrated at parse time or require manual upgrade? → A: Clean break — new config format required; old format produces a clear error with upgrade instructions.
- Q: Should transition duration be global or per-item configurable? → A: Per-item duration with a global default fallback; omitting duration on a sequence item uses the global default.

## Assumptions

- The hardware display grid remains 13x13 pixels with 12-bit brightness (0-4095).
- The GlyphMatrix SDK 2.0 API (`setMatrixFrame(IntArray)`) does not change as part of this refactor.
- Only one effect is active (rendering frames) at a time; effects cycle sequentially rather than compositing multiple effects simultaneously on the same frame.
- Filters are applied in the order they appear in configuration; no dependency ordering between filters is needed.
- The minimum frame interval of 30ms remains enforced across all layers.
- Font loading and text rendering (TextBitmapRenderer) remain unchanged; only how scrolling text is scheduled in the sequence changes.
- This refactor targets the service and config layers only; no UI changes are needed.
