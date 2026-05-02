# Research: Marquee Text Animation

## R1: Bitmap Font Format and Loading

**Decision**: Custom JSON format with per-character glyph maps. Loaded at startup via `org.json` (already a project dependency). Each font file contains: name, charHeight, topMargin, bottomMargin, defaultSpacing, and a glyphs map keyed by character string.

**Rationale**: JSON is the established asset format in this project (glyph patterns, toy config). Using `org.json` avoids new dependencies. The format is simple enough to hand-edit and easy to generate from the converter tool. Aligns with Constitution Principle V (Data-Driven Design).

**Alternatives considered**:
- BDF/PCF bitmap font formats: Require a parser library not currently in the project.
- Android `Typeface` at runtime for all fonts: Higher startup cost, inconsistent rendering at 11px, battery impact from Canvas operations.
- Embedded bitmap tables in TTF: Not all fonts include them; extraction is complex.

## R2: Text-to-Matrix Conversion Strategy

**Decision**: `TextBitmapRenderer` class performs one-time conversion at startup. For each character in the text, looks up glyph in the font, copies pixel rows into the wide matrix at the correct column offset, adds inter-character spacing (1px). The matrix is pre-padded with `GRID_SIZE` (13) blank columns on left and right so text fully enters and exits the viewport.

**Rationale**: Pre-computing the entire matrix means `generateFrame(tick)` is a trivial array copy (O(169) — 13 rows × 13 columns). No per-frame computation, branching, or bounds checking. Meets Constitution Principle II (Battery Discipline). Memory cost is negligible: a 200-character message at ~8px average width = ~1600 columns × 13 rows × 4 bytes ≈ 83KB.

**Alternatives considered**:
- Per-frame character lookup and rendering: Wasteful CPU per frame for the same result.
- Circular buffer / ring approach: Adds complexity for no benefit since we have the full text at startup.

## R3: Font Converter Tool

**Decision**: Python script using Pillow (`PIL.ImageFont`, `PIL.ImageDraw`, `PIL.Image`). Renders each character to a bitmap, reads pixels, thresholds to binary (configurable threshold, default 128), outputs the project's JSON font format.

**Rationale**: Pillow is the standard Python imaging library, handles TTF/OTF natively, and is trivial to install (`pip install Pillow`). The script is a build-time tool only — no runtime dependency. Supports configurable character height (default 11), top margin (default 1), and character set (default ASCII 32-126, or a file/range for Unicode).

**Alternatives considered**:
- fontTools: Good for font inspection but doesn't rasterize.
- Node.js with opentype.js: Different runtime; Python is simpler for a one-off tool.
- Manual pixel art only: Doesn't scale for full character sets.

## R4: Fade-to-Black Orchestration

**Decision**: Handle fade-to-black and fade-from-black in `GlyphToyService.startAnimation()`, not inside `ScrollingTextAnimation`. Reuse existing `CrossFadeTransition` with `sourceImage=currentImage, targetImage=IntArray(169){0}` (black) before the scroll, and the reverse after.

**Rationale**: `CrossFadeTransition` already implements smooth linear interpolation between any two images. Using it for fade-to-black is a zero-new-code solution. Keeping transition logic in the service layer (not the animation class) follows Constitution Principle IV (Animation as Interface) — the animation only generates marquee frames, the service orchestrates the sequence.

**Alternatives considered**:
- Embedding fade frames inside ScrollingTextAnimation: Violates single-responsibility; couples animation to base image knowledge.
- New FadeToBlackAnimation class: Unnecessary duplication of CrossFadeTransition.

## R5: Viewport Extraction and Frame Count

**Decision**: Wide text matrix dimensions: 13 rows × `(GRID_SIZE + textWidth + GRID_SIZE)` columns. `frameCount = textWidth + GRID_SIZE + 1`. At tick `t`, `generateFrame(t)` extracts columns `[t .. t+12]` into a 13×13 IntArray.

**Rationale**: Padding on both sides ensures text fully enters from right (first GRID_SIZE frames are blank) and fully exits left (last GRID_SIZE frames are blank). Simple index arithmetic, no bounds checks needed since the matrix is pre-padded. Frame count formula accounts for the viewport needing to slide from position 0 to position `textWidth + GRID_SIZE`.

**Alternatives considered**:
- No padding with bounds checking per frame: Error-prone, branching in hot path.
- Wrapping/looping scroll: Not specified in requirements; text should enter and exit cleanly.

## R6: Unicode Support and Missing Characters

**Decision**: Font JSON can include any Unicode character (keyed by the character as a string). At runtime, `TextBitmapRenderer` looks up each character in the font's glyph map. If missing, substitutes a blank glyph of width 5 (spec FR-011). No runtime fallback to system fonts.

**Rationale**: Full Unicode pre-computation for all code points is impractical (~150K characters). A practical font covers the characters actually used. The converter can be re-run with expanded character sets. Blank substitution for missing characters prevents crashes and is visually unambiguous. System font fallback would add runtime complexity and visual inconsistency.

**Alternatives considered**:
- Runtime system font fallback for missing characters: Adds Canvas dependency to animation path, violates Battery Discipline.
- Error/crash on missing character: Too strict for a display toy.
- Rendering a placeholder glyph (e.g., "?"): More complex than blank space, not specified.

## R7: Character Height and Margins

**Decision**: Default 11px character height with 1px top margin + 1px bottom margin = 13 rows. Alternate: 10px height with 2px top + 1px bottom. The font file declares its own margins; the renderer uses them.

**Rationale**: The grid is 13 rows. Centering text with small margins looks best on LEDs. Allowing the font file to declare its margins means different fonts can optimize their vertical positioning independently. The converter tool supports configurable `--char-height` (default 11) and `--top-margin` (default 1).

**Alternatives considered**:
- Full 13px (no margins): Text touches grid edges, looks cramped.
- Fixed margins in code: Less flexible for different font designs.
