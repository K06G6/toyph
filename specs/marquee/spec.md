# Feature Specification: Marquee Text Animation

**Feature Branch**: `marquee`  
**Created**: 2026-04-08  
**Status**: Draft  
**Input**: User description: "A new animation that displays scrolling marquee text across the LED grid, supporting bundled bitmap fonts and a font conversion tool."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Display Scrolling Text on AOD (Priority: P1)

As a toy user, I want to see a text message scroll across my phone's LED grid like a marquee sign, so that I can display custom messages on my Always-on Display.

The animation sequence is: the current glyph image fades to a blank (black) screen, the configured text scrolls from right to left across the grid (classic marquee), and after the text fully exits the screen, the original image fades back in. Other animations can then continue as normal.

**Why this priority**: This is the core feature — without the scroll animation rendering correctly, nothing else matters.

**Independent Test**: Can be fully tested by configuring a text message and font in the toy configuration and observing the animation on a physical device. Delivers the complete marquee experience.

**Acceptance Scenarios**:

1. **Given** a toy configuration with a scrolling text animation entry specifying text and a bundled font, **When** the AOD activates, **Then** the current image fades to black, the configured text scrolls smoothly from right to left across the 13x13 grid, and after the text exits the screen the original image fades back in.
2. **Given** a short text (fewer characters than the grid width), **When** the animation plays, **Then** the text still scrolls fully across and off the grid without visual glitches.
3. **Given** a long text message, **When** the animation plays, **Then** the entire message scrolls through without truncation, entering from the right edge and exiting from the left edge.
4. **Given** the scrolling text animation has completed, **When** additional animations are configured, **Then** the next animation begins normally after the image fades back in.

---

### User Story 2 - Use Custom Bitmap Fonts (Priority: P2)

As a toy creator, I want to bundle custom bitmap fonts designed for the 13-pixel-tall LED grid, so that my scrolling text has a distinctive visual style that looks great at low resolution.

Fonts are stored as asset files within the project. Each font defines per-character pixel maps at a fixed height that fits within the 13-row grid (with top and bottom margins). The toy configuration references which font to use for each scrolling text animation.

**Why this priority**: The quality of the text rendering depends entirely on having well-designed bitmap fonts. Without font support, text would be illegible or ugly on the LED grid.

**Independent Test**: Can be tested by creating a bitmap font file with a few characters, configuring it in the toy, and verifying the rendered characters look correct on the device.

**Acceptance Scenarios**:

1. **Given** a valid bitmap font file bundled in the project assets, **When** a scrolling text animation references that font, **Then** each character in the text is rendered using the corresponding glyph from the font.
2. **Given** the text contains a character not present in the font, **When** the animation renders, **Then** a blank space of standard width is displayed instead (no crash or corruption).
3. **Given** multiple bitmap fonts are available, **When** different scrolling text animations reference different fonts, **Then** each animation uses its specified font correctly.

---

### User Story 3 - Convert TrueType Fonts to Bitmap Format (Priority: P3)

As a toy creator, I want a conversion tool that takes a standard TrueType or OpenType font file and generates a bitmap font file suitable for the LED grid, so that I can easily bring any font into the project without manually drawing each character pixel by pixel.

The tool renders each character at the target pixel height, converts to binary (on/off) brightness, and outputs the result in the project's bitmap font format. It includes documentation explaining how to use it.

**Why this priority**: Without the converter, creating bitmap fonts is extremely tedious (manually defining pixel arrays for every character). The converter enables practical font authoring.

**Independent Test**: Can be tested by running the converter on a standard font file and verifying the output is a valid bitmap font file that renders correctly when used in a scrolling text animation.

**Acceptance Scenarios**:

1. **Given** a TTF or OTF font file, **When** the converter tool runs with default settings, **Then** it produces a valid bitmap font file covering ASCII printable characters (32-126).
2. **Given** a converter run with a custom character set parameter, **When** the tool runs, **Then** only the specified characters are included in the output.
3. **Given** the generated bitmap font file, **When** it is placed in the project assets and referenced in configuration, **Then** the scrolling text animation renders correctly using it.

---

### Edge Cases

- What happens when the configured text is empty? The animation should be skipped (no fade-to-black, no scroll).
- What happens when the referenced font file is missing or invalid? The animation should be skipped with a warning logged, and other animations continue normally.
- What happens when all characters in the text are missing from the font? A blank scroll plays (all-black frames scrolling through), followed by normal fade-back.
- What happens when the text contains mixed scripts (e.g., Latin and emoji)? Characters present in the font render normally; missing characters render as blank spaces.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support a new animation type that scrolls text across the 13x13 LED grid from right to left (classic marquee direction).
- **FR-002**: System MUST convert text to a pixel matrix (13 rows by variable columns) once at startup, not during animation playback.
- **FR-003**: Each animation frame MUST advance the viewport by exactly 1 column across the pre-computed text matrix.
- **FR-004**: System MUST support bundled bitmap font files that define per-character pixel maps at a height fitting within the 13-row grid.
- **FR-005**: Font files MUST include top and bottom margin definitions so that rendered text is vertically centered within the grid (default: 1px top, 1px bottom, 11px character height; or 2px top, 1px bottom, 10px character height).
- **FR-006**: System MUST render text with 1px spacing between characters.
- **FR-007**: Text brightness MUST be binary: pixels are either fully on (maximum brightness) or fully off.
- **FR-008**: Before the scrolling animation begins, the current displayed image MUST fade to a completely black screen.
- **FR-009**: After the scrolling animation completes, the screen MUST fade from black back to the original image.
- **FR-010**: System MUST support Unicode characters — any character present in the font file can be rendered.
- **FR-011**: Characters not present in the font MUST render as blank space (5 pixels wide) without causing errors.
- **FR-012**: The text, font, and scroll speed MUST be configurable through the toy configuration file.
- **FR-013**: A conversion tool MUST be provided that generates bitmap font files from standard TrueType (TTF) or OpenType (OTF) font files.
- **FR-014**: The conversion tool MUST include user documentation explaining installation, usage, and customization options.
- **FR-015**: The text matrix MUST include blank padding equal to the grid width on both sides, ensuring text fully enters from the right and fully exits to the left.

### Key Entities

- **Bitmap Font**: A collection of character glyphs at a fixed pixel height, with margin definitions. Each glyph has a pixel width and a binary pixel array. Fonts are identified by name and referenced by file path in configuration.
- **Character Glyph**: A single character's pixel representation within a font. Defined by its width and a flat array of binary brightness values (row-major order).
- **Text Matrix**: The full rendered text as a wide pixel grid (13 rows by many columns). Computed once from the text string and a bitmap font. Includes blank padding on both sides for smooth entry/exit.
- **Scrolling Text Animation Entry**: A configuration entry specifying the animation type, text content, font reference, scroll speed (frame interval), and post-scroll pause duration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Configured text scrolls smoothly across the LED grid with no visible stuttering or frame drops at the configured speed.
- **SC-002**: Text is legible on the physical LED grid — characters are distinguishable from each other when viewed at normal viewing distance.
- **SC-003**: The fade-to-black and fade-from-black transitions are visually smooth, matching the quality of existing image crossfade transitions.
- **SC-004**: Adding a scrolling text animation requires only a configuration change (font file + config entry) — no code modifications needed.
- **SC-005**: The font conversion tool can generate a bitmap font from a TTF/OTF file in under 10 seconds.
- **SC-006**: Battery impact of the scrolling animation is comparable to existing animations (no measurable increase in battery drain during AOD).
- **SC-007**: The animation integrates seamlessly with existing animation types — scrolling text can be mixed with scanline, phosphor trail, and other animations in the same configuration.

## Assumptions

- The target device is a Nothing phone with GlyphMatrix SDK support (currently Nothing Phone 4a Pro).
- The 13x13 LED grid hardware constraints (169 cells, 137 physically active) are unchanged.
- Existing crossfade transition logic can be reused for the fade-to-black and fade-from-black effects.
- A suitable open-source pixel font exists that renders legibly at 10-11px height for the initial bundled font.
- The font conversion tool requires Python with the Pillow library installed on the developer's machine (not a runtime dependency).
- The character set for the initial bundled font is ASCII printable (32-126); broader Unicode coverage can be added by re-running the converter with expanded character sets.
- The scrolling text animation follows the same lifecycle as existing animations: it is created per image in the animation loop and plays within the normal animation sequence.
