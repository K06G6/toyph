# Toyph Font Tools

## font-to-glyph.py

Converts a TrueType (TTF) or OpenType (OTF) font file into the Toyph bitmap font JSON format for use with the `scrolling_text` sequence item.

### Prerequisites

- Python 3.8+
- Pillow library

```bash
pip install Pillow
```

### Usage

**Basic conversion** (ASCII printable characters, 11px height):

```bash
python tools/font-to-glyph.py --input path/to/font.ttf --output app/src/main/assets/fonts/myfont.json --name myfont
```

**Custom character height** (10px with 2px top margin):

```bash
python tools/font-to-glyph.py \
  --input path/to/font.ttf \
  --output app/src/main/assets/fonts/myfont.json \
  --name myfont \
  --char-height 10 \
  --top-margin 2 \
  --bottom-margin 1
```

**Custom character set** (from a text file):

```bash
echo "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ." > my-chars.txt
python tools/font-to-glyph.py \
  --input path/to/font.ttf \
  --output app/src/main/assets/fonts/myfont.json \
  --charset my-chars.txt
```

**Adjust threshold** (lower = more pixels lit, higher = fewer):

```bash
python tools/font-to-glyph.py \
  --input path/to/font.ttf \
  --output app/src/main/assets/fonts/myfont.json \
  --threshold 64
```

**Override font size** (when auto-sizing doesn't look right):

```bash
python tools/font-to-glyph.py \
  --input path/to/font.ttf \
  --output app/src/main/assets/fonts/myfont.json \
  --font-size 14
```

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--input`, `-i` | (required) | Path to TTF/OTF font file |
| `--output`, `-o` | (required) | Path to output JSON file |
| `--name`, `-n` | `converted` | Font name identifier |
| `--char-height` | `11` | Pixel height of character glyphs |
| `--top-margin` | `1` | Blank rows above text in the 13-row grid |
| `--bottom-margin` | `1` | Blank rows below text in the 13-row grid |
| `--spacing` | `1` | Columns of blank space between characters |
| `--charset` | ASCII 32-126 | Path to text file containing characters to include |
| `--threshold` | `128` | Brightness threshold (0-255) for binary conversion |
| `--font-size` | auto | Font rendering size in points |

### Output Format

The generated JSON file follows the Toyph bitmap font schema:

```json
{
  "name": "myfont",
  "charHeight": 11,
  "topMargin": 1,
  "bottomMargin": 1,
  "defaultSpacing": 1,
  "glyphs": {
    "A": { "width": 7, "pixels": [0, 0, 4095, ...] },
    "B": { "width": 6, "pixels": [4095, 4095, ...] }
  }
}
```

Each glyph has a variable width and a flat row-major pixel array. Values are `0` (LED off) or `4095` (LED max brightness).

### Deploying to the Project

1. Place the generated JSON in `app/src/main/assets/fonts/`
2. Reference it from the `sequence` section in `toy-config.json`:

```json
{
  "type": "scrolling_text",
  "text": "YOUR TEXT HERE",
  "font": "fonts/myfont.json",
  "frameIntervalMs": 80,
  "brightness": 1.0
}
```

3. Build and deploy: `./gradlew installDebug`

## generate-test-font.py

Generates a hand-crafted minimal bitmap font (`default.json`) with uppercase A-Z, digits 0-9, space, and common punctuation. Each character is 5px wide, 11px tall. Used for development and testing.

```bash
python tools/generate-test-font.py
```

Output: `app/src/main/assets/fonts/default.json`
