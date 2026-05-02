#!/usr/bin/env python3
"""Convert a TTF/OTF font to a Toyph bitmap font JSON file.

Renders each character at the target pixel height using Pillow,
thresholds to binary brightness, and outputs the project's JSON
bitmap font format.

Requires: pip install Pillow
"""

import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def render_char(font: ImageFont.FreeTypeFont, char: str, char_height: int) -> tuple:
    """Render a single character and return (width, pixels)."""
    bbox = font.getbbox(char)
    if bbox is None:
        return None

    # Render to a temporary image large enough for any character
    img = Image.new("L", (char_height * 2, char_height * 2), 0)
    draw = ImageDraw.Draw(img)
    draw.text((0, 0), char, fill=255, font=font)

    # Crop to actual content
    bbox = img.getbbox()
    if bbox is None:
        # Fully blank character (e.g., space)
        # Return a space-width glyph
        return (char_height // 3, [0] * (char_height * (char_height // 3)))

    left, top, right, bottom = bbox

    # We want exactly char_height rows, so crop/pad vertically
    cropped = img.crop((left, 0, right, char_height))
    width = cropped.width

    if width < 1:
        return None

    pixels = []
    for y in range(char_height):
        for x in range(width):
            pixels.append(cropped.getpixel((x, y)))

    return (width, pixels)


def main():
    parser = argparse.ArgumentParser(
        description="Convert a TTF/OTF font to Toyph bitmap font JSON"
    )
    parser.add_argument("--input", "-i", required=True, help="Path to TTF/OTF font file")
    parser.add_argument("--output", "-o", required=True, help="Path to output JSON file")
    parser.add_argument("--name", "-n", default="converted", help="Font name identifier (default: converted)")
    parser.add_argument("--char-height", type=int, default=11, help="Pixel height for characters (default: 11)")
    parser.add_argument("--top-margin", type=int, default=1, help="Blank rows above text (default: 1)")
    parser.add_argument("--bottom-margin", type=int, default=1, help="Blank rows below text (default: 1)")
    parser.add_argument("--spacing", type=int, default=1, help="Columns between characters (default: 1)")
    parser.add_argument("--charset", default=None,
                        help="Path to a text file with characters to include, or omit for ASCII 32-126")
    parser.add_argument("--threshold", type=int, default=128,
                        help="Brightness threshold for binary conversion (0-255, default: 128)")
    parser.add_argument("--font-size", type=int, default=None,
                        help="Font size in points (default: auto-calculated from char-height)")
    args = parser.parse_args()

    grid_size = args.char_height + args.top_margin + args.bottom_margin
    if grid_size != 13:
        print(f"Warning: charHeight({args.char_height}) + topMargin({args.top_margin}) + "
              f"bottomMargin({args.bottom_margin}) = {grid_size}, expected 13", file=sys.stderr)

    # Determine character set
    if args.charset:
        charset_path = Path(args.charset)
        if not charset_path.exists():
            print(f"Error: charset file not found: {args.charset}", file=sys.stderr)
            sys.exit(1)
        chars = list(set(charset_path.read_text(encoding="utf-8").replace("\n", "").replace("\r", "")))
    else:
        chars = [chr(i) for i in range(32, 127)]

    chars.sort()

    # Load font
    font_path = Path(args.input)
    if not font_path.exists():
        print(f"Error: font file not found: {args.input}", file=sys.stderr)
        sys.exit(1)

    font_size = args.font_size if args.font_size else args.char_height
    pil_font = ImageFont.truetype(str(font_path), font_size)

    max_brightness = 4095
    threshold = args.threshold

    glyphs = {}
    skipped = []

    for char in chars:
        result = render_char(pil_font, char, args.char_height)
        if result is None:
            skipped.append(char)
            continue

        width, raw_pixels = result

        # Threshold to binary
        pixels = [max_brightness if p >= threshold else 0 for p in raw_pixels]

        # Verify pixel count
        expected = args.char_height * width
        if len(pixels) != expected:
            # Pad or truncate
            if len(pixels) < expected:
                pixels.extend([0] * (expected - len(pixels)))
            else:
                pixels = pixels[:expected]

        glyphs[char] = {"width": width, "pixels": pixels}

    font_data = {
        "name": args.name,
        "charHeight": args.char_height,
        "topMargin": args.top_margin,
        "bottomMargin": args.bottom_margin,
        "defaultSpacing": args.spacing,
        "glyphs": glyphs,
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(font_data, f, ensure_ascii=False)
        f.write("\n")

    print(f"Generated '{args.name}' font with {len(glyphs)} glyphs")
    print(f"  Character height: {args.char_height}px (margins: {args.top_margin}px top, {args.bottom_margin}px bottom)")
    print(f"  Threshold: {threshold}/255")
    print(f"  Output: {output_path}")
    if skipped:
        print(f"  Skipped {len(skipped)} characters: {''.join(skipped)}")


if __name__ == "__main__":
    main()
