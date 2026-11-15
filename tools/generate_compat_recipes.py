#!/usr/bin/env python3
"""Writes or verifies the Ice and Fire dragon-scale armor recipes for the Leatherworker's Bench and the scale-backed tags.
  python3 tools/generate_compat_recipes.py            # --check (default): compare with the files on disk, write nothing
  python3 tools/generate_compat_recipes.py --write    # write every file (and remove stale compat recipes)

Verified on 2026-10-17 from the jars themselves (recipe JSON, item ids, armor classes, scale tags):
  - Ice and Fire 2.1.13-beta-5 for 1.20.1 (iceandfire-2.1.13-1.20.1-beta-5.jar; mod id iceandfire; needs Citadel)
  - Ice and Fire: Community Edition 1.2.7 for 1.20.1 (IceAndFireCE-1.2.7-1.20.1-forge.jar; same mod id; needs Uranus
    and Jupiter)
Both register iceandfire:armor_<colour>_<slot> for twelve colours and craft every piece with a shaped recipe of the same
id from that colour's iceandfire:dragonscales_<colour> alone: helmet 5, chestplate 8, leggings 7, boots 4. The one
difference: the Community Edition spells the twelfth colour "amethyst" in its item ids, while its recipe files keep
the original ids armor_amythest_<slot>; the original build spells it "amythest" everywhere. That colour ships twice,
each copy behind forge:item_exists and both replacing the same recipe id, so one datapack serves both builds.

The colour list only names ids. Which colours breathe fire, ice or lightning comes from Ice and Fire's own scale tags
(iceandfire:scales/dragon/fire, ice, lightning), never from a colour's name; nothing here depends on it.
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / 'src/main/resources/data/immersive_tanning'
TEST = ROOT / 'src/gameTest/resources/data/immersive_tanning'
RECIPES = MAIN / 'recipes/leatherworking/compat/iceandfire'

# (colour in the item ids, colour in the crafting recipe id). They differ only for the Community Edition's amethyst.
COLOURS = [(c, c) for c in ('red', 'green', 'bronze', 'gray', 'blue', 'sapphire', 'silver', 'white', 'electric', 'copper', 'black')]
COLOURS += [('amythest', 'amythest'), ('amethyst', 'amythest')]
# Slot: scales in Ice and Fire's own recipe, finished leather for the backing.
SLOTS = {'helmet': (5, 1), 'chestplate': (8, 2), 'leggings': (7, 2), 'boots': (4, 1)}
# The GameTest datapack's copy of the tag (it shadows the main one) also makes vanilla diamond armor scale-backed, the
# stand-in the tests stitch, since Ice and Fire is not loaded there.
TEST_STAND_INS = ['minecraft:diamond_helmet', 'minecraft:diamond_chestplate', 'minecraft:diamond_leggings', 'minecraft:diamond_boots']


def recipe(item_colour, recipe_colour, slot):
    scales, leather = SLOTS[slot]
    armor = f'iceandfire:armor_{item_colour}_{slot}'
    scale = f'iceandfire:dragonscales_{item_colour}'
    return {
        'type': 'immersive_tanning:leatherworking',
        'conditions': [
            {'type': 'forge:mod_loaded', 'modid': 'iceandfire'},
            {'type': 'forge:item_exists', 'item': armor},
            {'type': 'forge:item_exists', 'item': scale},
        ],
        'leather': leather,
        'auxiliary': [{'ingredient': {'item': scale}, 'count': scales}],
        'result': {'item': armor},
        'seam': f'immersive_tanning:{slot}',
        'replaces': [f'iceandfire:armor_{recipe_colour}_{slot}'],
    }


def tag(required=()):
    optional = [{'id': f'iceandfire:armor_{c}_{slot}', 'required': False} for c, _ in COLOURS for slot in SLOTS]
    return {'values': list(required) + optional}


def expected():
    files = {}
    for item_colour, recipe_colour in COLOURS:
        for slot in SLOTS:
            files[RECIPES / f'armor_{item_colour}_{slot}.json'] = recipe(item_colour, recipe_colour, slot)
    files[MAIN / 'tags/items/scale_backed.json'] = tag()
    files[TEST / 'tags/items/scale_backed.json'] = tag(TEST_STAND_INS)
    return {path: json.dumps(data, indent=2) + '\n' for path, data in files.items()}


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    args = parser.parse_args()
    files = expected()
    stale = sorted(p for p in RECIPES.glob('*.json') if p not in files) if RECIPES.is_dir() else []
    if args.write:
        for path, text in files.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        for path in stale:
            path.unlink()
        print(f'wrote {len(files)} files, removed {len(stale)} stale recipes')
        return 0
    differing = []
    for path, text in files.items():
        if not path.exists():
            differing.append(f'missing {path.relative_to(ROOT)}')
        elif path.read_text() != text:
            differing.append(f'differs {path.relative_to(ROOT)}')
    differing += [f'stale {path.relative_to(ROOT)}' for path in stale]
    for line in differing:
        print(line)
    print(f'PASS - {len(files)} files' if not differing else f'FAIL - {len(differing)} files differ from tools/generate_compat_recipes.py')
    return 0 if not differing else 1


if __name__ == '__main__':
    sys.exit(main())
