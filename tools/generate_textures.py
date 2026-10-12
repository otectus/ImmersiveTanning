#!/usr/bin/env python3
"""Exports or verifies the artwork defined in art/tanning_art.py.

  python3 tools/generate_textures.py            # --check (default): compare exports, write nothing
  python3 tools/generate_textures.py --write    # export every texture into the resource folder
  python3 tools/generate_textures.py --preview out.png   # an upscaled contact sheet for review
"""
import argparse
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'art'))
import tanning_art  # noqa: E402

TEXTURES = ROOT / 'src/main/resources/assets/immersive_tanning/textures'
# Textures of the retired Stoneborn GUI. The screens draw Minecraft's own GUI textures now; these must never come back.
RETIRED = ['gui/stone.png', 'gui/slot.png']


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    group.add_argument('--preview')
    args = parser.parse_args()
    art = tanning_art.textures()

    if args.preview:
        scale = 8
        cols = 6
        rows = (len(art) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * 34 * scale, rows * 34 * scale), (58, 50, 44, 255))
        for i, (name, im) in enumerate(sorted(art.items())):
            big = im.resize((im.width * scale * (32 // im.width if im.width < 32 else 1), im.height * scale * (32 // im.height if im.height < 32 else 1)), Image.NEAREST)
            sheet.alpha_composite(big, ((i % cols) * 34 * scale + scale, (i // cols) * 34 * scale + scale))
        sheet.save(args.preview)
        print(f'preview: {args.preview} ({len(art)} textures)')
        return 0

    if args.write:
        for name, im in art.items():
            path = TEXTURES / name
            path.parent.mkdir(parents=True, exist_ok=True)
            im.save(path)
        for name in RETIRED:
            (TEXTURES / name).unlink(missing_ok=True)
        print(f'wrote {len(art)} textures')
        return 0

    differing = []
    for name, im in art.items():
        path = TEXTURES / name
        if not path.exists():
            differing.append(f'missing {name}')
            continue
        on_disk = Image.open(path).convert('RGBA')
        # RGBA getbbox() defaults to alpha-only: equal alpha could hide changed RGB pixels.
        if on_disk.size != im.size or on_disk.tobytes() != im.tobytes():
            differing.append(f'differs {name}')
    for name in RETIRED:
        if (TEXTURES / name).exists():
            differing.append(f'retired {name}')
    for line in differing:
        print(line)
    print('PASS' if not differing else f'FAIL - {len(differing)} textures differ from art/tanning_art.py')
    return 0 if not differing else 1


if __name__ == '__main__':
    sys.exit(main())
