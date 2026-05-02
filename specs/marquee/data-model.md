# Data Model: Marquee Text Animation

## Entities

### TextBitmapFont (loaded from JSON asset)

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| name | String | Font identifier (e.g., "default", "rebel-mono") | Non-empty |
| charHeight | Int | Pixel height of glyphs (10 or 11) | 8-13; charHeight + topMargin + bottomMargin = 13 |
| topMargin | Int | Blank rows above text (1 or 2) | 0-3 |
| bottomMargin | Int | Blank rows below text (1) | 0-3 |
| defaultSpacing | Int | Columns between characters | 0-5, default 1 |
| glyphs | Map<String, GlyphBitmap> | Character → bitmap mapping. Key is the character as a string. | At least one glyph required |

### GlyphBitmap (nested in font)

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| width | Int | Pixel width of this character | 1-13 |
| pixels | IntArray | Flat array, row-major, charHeight × width. Binary brightness. | Length = charHeight × width; values ∈ {0, 4095} |

### TextMatrix (computed at startup, not persisted)

| Field | Type | Description |
|-------|------|-------------|
| width | Int | Total columns: GRID_SIZE + textWidth + GRID_SIZE (padded for entry/exit) |
| data | IntArray | Flat array, 13 rows × width columns, row-major. Top/bottom margin rows are zero-filled. |

### AnimationConfig (extended from existing)

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| type | String | Animation type name (existing field) | Required |
| frameIntervalMs | Long | Milliseconds between frames (existing field) | >= 30 (enforced by ToyConfig) |
| trailingIntervalMs | Long | Pause after animation (existing field) | >= 0 |
| text | String? | Text to display. Only for SCROLLING_TEXT. | Non-empty when type = SCROLLING_TEXT |
| font | String? | Asset path to font JSON. Only for SCROLLING_TEXT. | Valid asset path when type = SCROLLING_TEXT |

## Relationships

```
toy-config.json
  └── animations[]
        └── AnimationConfig {type: "SCROLLING_TEXT", text, font}
              ├── font path → fonts/*.json → TextBitmapFont
              │     └── glyphs → Map<String, GlyphBitmap>
              └── text + font → TextBitmapRenderer.renderText() → TextMatrix
                    └── ScrollingTextAnimation(textMatrix) → 13x13 frames
```

## Validation Rules

1. `text` must be non-empty when type is `SCROLLING_TEXT` (FR-012, edge case: empty text → skip animation)
2. `font` must be a valid asset path when type is `SCROLLING_TEXT` (edge case: missing font → skip with warning)
3. Font JSON `charHeight + topMargin + bottomMargin` must equal `GRID_SIZE` (13) (FR-005)
4. All pixel values in font glyphs must be 0 or `MAX_BRIGHTNESS` (4095) (FR-007)
5. Each glyph's pixel array length must equal `charHeight × width`
6. `frameIntervalMs` >= 30 (Constitution II, enforced by existing ToyConfig parsing)
7. Characters missing from font render as blank space of width 5 (FR-011)

## State Transitions

```
Service.onBind()
  → Load config (ToyConfig.parse)
  → Load font JSONs referenced by SCROLLING_TEXT animations
  → Load glyph images
  → Cache fonts in memory

EVENT_AOD / EVENT_CHANGE
  → startAnimation()
    → For each image:
      → For each animation:
        [if SCROLLING_TEXT]:
          → TextBitmapRenderer.renderText(text, font) → TextMatrix
          → CrossFadeTransition(currentImage → black)        [fade to black]
          → ScrollingTextAnimation.generateFrame(0..N)       [marquee scroll]
          → CrossFadeTransition(black → currentImage)        [fade back]
        [else]:
          → existing animation flow (generateFrame loop + baseFrame + trailing)
      → CrossFadeTransition to next image (if multiple images)
```
