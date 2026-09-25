"""Editable pixel-art source for Immersive Tanning. Deterministic: no random noise, no resampling.

PNG files are delivery exports. Change this source, then run tools/generate_textures.py --write.
Block model JSON in assets/immersive_tanning/models is directly editable in Blockbench (Java Block/Item).
Tool *_held.json files contain solid geometry; their item parents select these models outside the GUI.
Inventory icons remain hand-placed 16px sprites, with material ramps matching Immersive Smithing.
"""
import math
from PIL import Image, ImageDraw

# Material ramps, darkest first.
FUR = ['#292021', '#483027', '#694632', '#8b6140', '#ad8254', '#d0ad76']
SCRAPED = ['#5a4332', '#8a6d52', '#b89b78', '#d6bd98', '#eadabb', '#f6ecd6']
WET = ['#251c1d', '#432a24', '#63402e', '#80543a', '#aa8054', '#d3b680']
FLESH = ['#5e2a28', '#8a3f3c', '#b25a55', '#cf7e74', '#e3a495']
SUPPLE = ['#4f3419', '#7d5a30', '#a98049', '#cda266', '#e3c08a', '#f3dcb0']
HARNESS = ['#2f170c', '#522915', '#7a3f20', '#9c5a30', '#bb7646', '#d59a68']
HARDENED = ['#251d20', '#3e2926', '#5a3a2e', '#79503a', '#a47950', '#c3a070']
IRON = ['#20282f', '#4a5861', '#7c909a', '#bdc9c7', '#f0eee0']
# Shared material language with Immersive Smithing: warm timber, cool iron, bronze collars.
WOOD = ['#292321', '#42332b', '#604735', '#806044', '#a08055', '#bea171']
FORGED = ['#171c22', '#2a3037', '#3d4852', '#596773', '#87949b', '#b6bfb9']
BRONZE = ['#49352c', '#755039', '#a77d4f', '#d2ad6b']
BINDING = ['#3f2616', '#6a4226', '#946034', '#b98249', '#d6a468', '#ecc894']
BONE = ['#6f6656', '#9d937c', '#c9bfa6', '#e8e0cc']
BARK = ['#22160d', '#3b2819', '#574029', '#735a3b', '#8f7650']
THREAD = '#e8d6b0'


def canvas(size, color=(0, 0, 0, 0)):
    im = Image.new('RGBA', size, color)
    return im, ImageDraw.Draw(im)


def hash2(x, y, salt=0):
    """Deterministic per-pixel value in 0..255 (not random: a fixed integer hash)."""
    h = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


# ---------------------------------------------------------------------------------------------- hides

def _capsule(px, py, ax, ay, bx, by, r):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(ax + dx * t - px, ay + dy * t - py) <= r


def pelt_mask(n, scale):
    """A stretched pelt on an n x n grid: body, four splayed legs, a head and a tail. Scale shrinks it toward the centre."""
    k = n / 16.0
    c = 7.5 * k + (k - 1) * 0.5

    def s(v):
        return c + (v - 7.5) * k * scale

    mask = [[False] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            px, py = x + 0.5 - 0.5 * (k - 1) + (k - 1) * 0.5, y + 0.5
            body = ((px - s(7.5)) / (5.5 * k * scale)) ** 2 + ((py - s(7.9)) / (5.0 * k * scale)) ** 2 <= 1.0
            legs = any(_capsule(px, py, s(ax), s(ay), s(bx), s(by), 1.55 * k * scale)
                       for ax, ay, bx, by in ((5.2, 5.6, 2.4, 2.7), (9.8, 5.6, 12.6, 2.7),
                                              (5.2, 10.4, 2.4, 13.1), (9.8, 10.4, 12.6, 13.1)))
            head = math.hypot(px - s(7.5), py - s(2.5)) <= 2.0 * k * scale
            tail = _capsule(px, py, s(7.5), s(12.0), s(7.5), s(14.4), 0.75 * k * scale)
            mask[y][x] = body or legs or head or tail
    return mask


# Hand-cut outlines keep the three sizes legible even in a 16px inventory slot.
PELT_OUTLINES = {
    'small': [(5, 3), (6, 4), (8, 3), (10, 4), (12, 3), (12, 5), (11, 6), (11, 9),
              (12, 11), (11, 12), (9, 11), (7, 12), (5, 11), (3, 12), (3, 10), (4, 8), (4, 6), (3, 4)],
    'medium': [(3, 2), (5, 4), (6, 4), (7, 2), (9, 2), (10, 4), (12, 2), (13, 4),
               (11, 6), (11, 9), (13, 11), (12, 13), (10, 11), (8, 13), (6, 11),
               (3, 13), (2, 11), (4, 9), (4, 6), (2, 4)],
    'large': [(2, 1), (4, 2), (5, 4), (6, 3), (7, 1), (9, 1), (10, 4), (12, 2), (14, 1),
              (14, 4), (12, 6), (12, 9), (14, 12), (14, 14), (12, 14), (10, 12),
              (9, 13), (8, 15), (7, 15), (6, 12), (3, 14), (1, 14), (2, 11), (3, 9), (3, 6), (1, 4)],
}


def hide_items():
    out = {}
    for size, outline in PELT_OUTLINES.items():
        mask = Image.new('1', (16, 16))
        ImageDraw.Draw(mask).polygon(outline, fill=1)
        for stage, ramp in [('raw', FUR), ('scraped', SCRAPED), ('wet', WET)]:
            im, d = canvas((16, 16))

            def inside(x, y):
                return 0 <= x < 16 and 0 <= y < 16 and mask.getpixel((x, y))

            for y in range(16):
                for x in range(16):
                    if not inside(x, y):
                        continue
                    edge = not all(inside(x + dx, y + dy) for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)])
                    upper_edge = not inside(x - 1, y - 1)
                    lower_edge = not inside(x + 1, y + 1)
                    idx = 0 if edge else 4 if upper_edge else 1 if lower_edge else 3
                    if not edge and not upper_edge and not lower_edge:
                        # Broad, deliberately clustered spine/shoulder planes, no pixel noise.
                        if stage == 'raw':
                            idx = 2 if x >= 9 else 4 if 5 <= x <= 7 and 5 <= y <= 10 else 3
                        elif stage == 'scraped':
                            idx = 4 if x < 8 else 3
                        else:
                            idx = 2 if x >= 8 else 3
                    d.point((x, y), fill=ramp[idx])
            if stage == 'raw':
                marks = [(5, 5, 4), (6, 6, 5), (5, 8, 4), (6, 9, 4), (8, 4, 4),
                         (9, 7, 1), (10, 8, 2), (9, 10, 1), (7, 11, 2)]
            elif stage == 'scraped':
                marks = [(5, 6, 5), (6, 6, 5), (6, 5, 5), (7, 9, 3), (8, 9, 3),
                         (9, 8, 2), (9, 11, 2), (10, 11, 2), (5, 10, 4)]
            else:
                marks = [(5, 5, 4), (6, 5, 5), (6, 6, 4), (7, 6, 4), (7, 7, 3),
                         (5, 9, 4), (6, 10, 4), (9, 10, 1), (10, 10, 1)]
            for x, y, idx in marks:
                if all(inside(x + dx, y + dy) for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]):
                    d.point((x, y), fill=ramp[idx])
            name = f'{size}_hide' if stage == 'raw' else f'{stage}_hide_{size}' if stage == 'scraped' else f'wet_tanned_hide_{size}'
            out[f'item/{name}.png'] = im
    return out


# ---------------------------------------------------------------------------------------------- leathers

def supple_leather():
    """Soft ochre leather, draped diagonally with a turned-back corner and folded hem."""
    r = SUPPLE
    im, d = canvas((16, 16))
    d.polygon([(2, 4), (6, 2), (12, 1), (12, 4), (14, 10), (13, 12), (9, 14), (6, 13),
               (3, 14), (1, 12), (2, 8)], fill=r[0])
    d.polygon([(3, 4), (10, 2), (11, 4), (13, 10), (12, 11), (9, 13), (6, 11), (3, 12)], fill=r[3])
    d.line([(3, 5), (6, 3), (10, 2)], fill=r[5])
    d.polygon([(3, 6), (5, 5), (5, 9), (7, 12), (6, 12), (3, 11)], fill=r[2])
    d.line([(6, 5), (7, 8), (7, 10), (9, 12)], fill=r[4])
    d.line([(9, 4), (10, 7), (12, 10)], fill=r[2])
    d.line([(10, 3), (11, 7), (12, 9)], fill=r[4])
    d.line([(2, 11), (3, 13), (5, 12)], fill=r[4])
    d.line([(9, 13), (12, 12), (13, 11)], fill=r[1])
    d.point((4, 7), fill=r[4])
    return im


def harness_leather():
    """A thick chestnut roll, with a visible spiral end and a narrow leather tie."""
    r = HARNESS
    im, d = canvas((16, 16))
    d.polygon([(4, 1), (11, 1), (13, 3), (13, 9), (14, 10), (14, 13), (12, 15),
               (3, 15), (1, 13), (1, 10), (3, 8), (3, 3)], fill=r[0])
    d.polygon([(4, 3), (5, 2), (10, 2), (12, 3), (12, 10), (4, 10)], fill=r[3])
    d.line((5, 2, 10, 2), fill=r[5]); d.line((4, 3, 4, 8), fill=r[4])
    d.line((11, 4, 11, 9), fill=r[2]); d.line((6, 6, 7, 5), fill=r[4])
    for y, x0, x1, color in [(9, 4, 12, r[2]), (10, 3, 13, r[4]), (11, 2, 13, r[5]),
                             (12, 2, 13, r[3]), (13, 3, 13, r[2]), (14, 4, 12, r[1])]:
        d.line((x0, y, x1, y), fill=color)
    d.rectangle((2, 11, 4, 13), fill=r[1]); d.point((3, 11), fill=r[5]); d.point((3, 12), fill=r[3])
    d.line((8, 9, 8, 14), fill=BINDING[0]); d.line((9, 10, 9, 14), fill=BINDING[4])
    d.point((9, 11), fill=BRONZE[3]); d.point((10, 12), fill=BINDING[1])
    return im


def hardened_leather():
    """A rigid waxed panel with a cut bevel, overlapping a second dark edge."""
    r = HARDENED
    im, d = canvas((16, 16))
    d.polygon([(1, 5), (11, 2), (14, 4), (14, 12), (5, 15), (1, 12)], fill=r[0])
    d.polygon([(2, 5), (11, 3), (13, 4), (13, 11), (5, 13), (2, 11)], fill=r[3])
    d.line([(2, 5), (11, 3), (12, 4)], fill=r[5])
    d.line((2, 6, 2, 10), fill=r[4]); d.line((3, 6, 10, 4), fill=r[4])
    d.line([(3, 11), (5, 12), (12, 10), (12, 5)], fill=r[1])
    d.line([(5, 14), (13, 12)], fill=r[2])
    d.line((5, 7, 7, 6), fill=r[4]); d.line((8, 9, 10, 8), fill=r[2])
    d.point((4, 9), fill=r[2]); d.point((10, 5), fill=r[4])
    return im


def binding_leather():
    """A folded binding blank with a stitched spine and layered cut edges."""
    r = BINDING
    im, d = canvas((16, 16))
    outline = [(1, 4), (6, 2), (8, 3), (12, 1), (14, 3), (14, 12), (9, 14), (7, 13), (3, 15), (1, 13)]
    d.polygon(outline, fill=r[0])
    d.polygon([(2, 5), (6, 3), (7, 4), (7, 11), (3, 13), (2, 12)], fill=r[2])
    d.polygon([(8, 4), (12, 2), (13, 3), (13, 11), (9, 13), (8, 11)], fill=r[3])
    d.line([(2, 5), (6, 3)], fill=r[4]); d.line((9, 4, 12, 3), fill=r[5])
    d.line((9, 5, 9, 10), fill=r[4]); d.line((12, 5, 12, 10), fill=r[2])
    d.line((7, 4, 7, 11), fill=r[1]); d.line((3, 14, 6, 13), fill=r[4])
    d.line((10, 13, 13, 12), fill=r[1])
    for y in (5, 8, 11):
        d.line((6, y, 7, y + 1), fill=THREAD)
    return im


def scraps():
    im, d = canvas((16, 16))
    d.polygon([(2, 2), (6, 1), (8, 4), (5, 7), (1, 5)], fill=HARNESS[0])
    d.polygon([(2, 3), (5, 2), (6, 4), (4, 5), (2, 4)], fill=HARNESS[3])
    d.line((2, 3, 5, 2), fill=HARNESS[5])
    d.polygon([(9, 4), (13, 3), (15, 7), (12, 10), (7, 8)], fill=SUPPLE[0])
    d.polygon([(9, 5), (12, 4), (14, 7), (11, 8), (8, 7)], fill=SUPPLE[3])
    d.line((9, 5, 12, 4), fill=SUPPLE[5]); d.line((12, 8, 13, 7), fill=SUPPLE[2])
    d.polygon([(4, 9), (8, 9), (10, 13), (5, 15), (1, 12)], fill=HARDENED[0])
    d.polygon([(4, 10), (7, 10), (8, 12), (5, 13), (3, 12)], fill=HARDENED[3])
    d.line((4, 10, 7, 10), fill=HARDENED[5]); d.point((5, 12), fill=HARDENED[2])
    return im


def bark():
    """Split tannin bark with a curled pale inner lip and stepped longitudinal grain."""
    im, d = canvas((16, 16))
    d.polygon([(2, 9), (8, 1), (11, 2), (12, 4), (6, 12), (3, 13), (1, 11)], fill=BARK[0])
    d.polygon([(3, 9), (8, 2), (10, 3), (9, 5), (5, 11), (3, 11)], fill=BARK[3])
    d.line([(3, 10), (5, 7), (6, 7), (9, 3)], fill=BARK[4])
    d.line([(5, 10), (7, 8), (7, 7), (10, 4)], fill=BARK[1])
    d.polygon([(6, 12), (12, 5), (14, 5), (15, 8), (10, 15), (7, 15)], fill=BARK[0])
    d.polygon([(7, 12), (12, 6), (13, 7), (13, 9), (9, 14), (7, 14)], fill=BARK[2])
    d.line([(8, 12), (10, 10), (10, 9), (12, 7)], fill=BARK[4])
    d.line((10, 13, 13, 9), fill=BARK[1])
    d.line((12, 6, 13, 6), fill=BINDING[5]); d.point((14, 7), fill=BINDING[3])
    d.point((7, 13), fill=BINDING[4]); d.point((8, 14), fill=BINDING[2])
    return im


def knife():
    """Broad half-moon skinning blade, warm turned grip and bronze ferrule."""
    im, d = canvas((16, 16))
    d.polygon([(1, 12), (5, 8), (7, 8), (8, 10), (4, 14), (2, 14)], fill=WOOD[0])
    d.line((2, 12, 5, 9), fill=WOOD[4]); d.line((3, 13, 6, 10), fill=WOOD[2])
    d.point((2, 13), fill=WOOD[1]); d.point((4, 11), fill=WOOD[5])
    d.line((5, 8, 7, 10), fill=BRONZE[0]); d.line((6, 8, 8, 10), fill=BRONZE[2])
    d.point((6, 8), fill=BRONZE[3])
    d.polygon([(6, 6), (7, 3), (10, 1), (13, 1), (14, 2), (14, 5), (12, 8), (10, 9), (8, 8)], fill=IRON[0])
    d.polygon([(7, 6), (8, 3), (10, 2), (13, 2), (13, 5), (11, 7), (10, 8)], fill=IRON[2])
    d.line([(8, 4), (10, 2), (12, 2)], fill=IRON[3])
    d.line([(13, 2), (13, 5), (11, 7), (10, 8)], fill=IRON[4])
    d.line([(7, 6), (9, 7), (10, 7)], fill=IRON[1])
    d.line((10, 3, 11, 3), fill=IRON[3]); d.point((12, 5), fill=IRON[3])
    return im


def awl():
    """Faceted wooden palm handle, bronze collar and a bright tapered steel point."""
    im, d = canvas((16, 16))
    d.polygon([(1, 11), (3, 9), (5, 8), (7, 10), (7, 12), (4, 15), (2, 15), (1, 14)], fill=WOOD[0])
    d.polygon([(2, 11), (4, 9), (5, 9), (6, 10), (6, 12), (3, 14), (2, 13)], fill=WOOD[3])
    d.line((2, 11, 4, 9), fill=WOOD[5]); d.line((3, 12, 5, 10), fill=WOOD[4])
    d.line((4, 13, 6, 11), fill=WOOD[1]); d.point((3, 14), fill=WOOD[2])
    d.line((5, 8, 7, 10), fill=BRONZE[0]); d.line((6, 8, 8, 10), fill=BRONZE[2])
    d.point((6, 8), fill=BRONZE[3])
    d.line((7, 7, 13, 1), fill=IRON[0]); d.line((8, 8, 14, 2), fill=IRON[1])
    d.line((7, 8, 13, 2), fill=IRON[3]); d.line((7, 7, 12, 2), fill=IRON[4])
    return im


# ---------------------------------------------------------------------------------------------- blocks

def worked_timber():
    im, d = canvas((16, 16), WOOD[2])
    for x in (0, 5, 10):
        d.line((x, 0, x, 15), fill=WOOD[0]); d.line((x + 1, 0, x + 1, 15), fill=WOOD[3])
    for points in [[(3, 1), (3, 4), (2, 5), (2, 8)], [(8, 5), (7, 6), (7, 12)],
                   [(13, 0), (13, 3), (12, 4), (12, 7)], [(13, 10), (14, 11), (14, 15)]]:
        d.line(points, fill=WOOD[1])
    for x, y in [(2, 1), (7, 2), (12, 8), (2, 11)]:
        d.line((x, y, x, y + 2), fill=WOOD[4])
    return im


def timber_end():
    im, d = canvas((16, 16), WOOD[2])
    for a, idx in [(0, 0), (1, 3), (3, 1), (4, 4), (6, 1)]:
        d.rectangle((a, a, 15 - a, 15 - a), outline=WOOD[idx])
    d.line((8, 0, 8, 3), fill=WOOD[0]); d.line((12, 11, 15, 13), fill=WOOD[0])
    return im


def vat_band():
    """Two repeatable 8px bands; narrow model strips sample the highlighted top row."""
    im, d = canvas((16, 16), FORGED[2])
    for y in (0, 8):
        d.line((0, y, 15, y), fill=FORGED[4]); d.line((0, y + 7, 15, y + 7), fill=FORGED[0])
        d.line((0, y + 6, 15, y + 6), fill=FORGED[1])
        for x in (2, 12):
            d.rectangle((x, y + 3, x + 1, y + 4), fill=FORGED[0])
            d.point((x, y + 3), fill=BRONZE[2]); d.point((x + 1, y + 3), fill=BRONZE[3])
    return im


def vat_staves():
    im, d = canvas((16, 16), WOOD[2])
    for x in (0, 4, 8, 12):
        d.line((x, 0, x, 15), fill=WOOD[0]); d.line((x + 1, 0, x + 1, 11), fill=WOOD[4])
        d.line((x + 2, 2, x + 2, 9), fill=WOOD[3])
    for points in [[(3, 2), (2, 3), (2, 5)], [(7, 6), (6, 7), (6, 10)], [(11, 1), (10, 2), (10, 4)],
                   [(15, 8), (14, 9), (14, 11)]]:
        d.line(points, fill=WOOD[1])
    for x, y in [(1, 12), (5, 10), (9, 13), (13, 11)]:
        d.rectangle((x, y, x + 2, 15), fill=WOOD[1]); d.line((x, y, x, y + 1), fill=WOOD[2])
    return im


def vat_inner():
    im, d = canvas((16, 16), '#423025')
    for x in (0, 4, 8, 12):
        d.line((x, 0, x, 15), fill='#251f1c'); d.line((x + 1, 0, x + 1, 8), fill='#584030')
    d.rectangle((0, 12, 15, 15), fill='#30241e')
    for x, y in [(2, 4), (6, 7), (10, 2), (14, 6)]:
        d.line((x, y, x, y + 3), fill='#37271f')
    return im


def bench_mat():
    """Burnished work mat with a stitched border and restrained clustered tool marks."""
    r = HARNESS
    im, d = canvas((16, 16), r[3])
    d.rectangle((0, 0, 15, 15), outline=r[0]); d.rectangle((1, 1, 14, 14), outline=r[2])
    d.line((2, 2, 13, 2), fill=r[4]); d.line((2, 3, 2, 12), fill=r[4])
    for i in (3, 6, 9, 12):
        d.point((i, 1), fill=THREAD); d.point((i, 14), fill=BINDING[4])
        d.point((1, i), fill=THREAD); d.point((14, i), fill=BINDING[4])
    for line in [(4, 6, 6, 5), (8, 9, 11, 7), (5, 11, 7, 10)]:
        d.line(line, fill=r[2])
    d.line((8, 5, 10, 4), fill=r[4]); d.point((11, 11), fill=r[4])
    return im


def rack_lashing():
    im, d = canvas((16, 16), '#9e8056')
    for y in range(0, 16, 4):
        d.line((0, y, 15, y), fill='#49382a')
        d.line((0, y + 1, 15, y + 1), fill='#d3b584')
        d.line((0, y + 3, 15, y + 3), fill='#715638')
        for x in range(1, 16, 4):
            d.point((x, y + 1), fill='#b79969'); d.point((x + 1, y + 2), fill='#715638')
    return im


def tool_materials():
    """16px atlas: iron, bronze, wood, end grain and thread in separate 4px squares."""
    im, d = canvas((16, 16), FORGED[1])
    for x, y, ramp in [(0, 0, IRON), (4, 0, BRONZE), (8, 0, WOOD), (12, 0, BONE)]:
        d.rectangle((x, y, x + 3, y + 3), fill=ramp[2])
        d.line((x, y, x + 3, y), fill=ramp[-1]); d.line((x, y + 1, x, y + 2), fill=ramp[-2])
        d.line((x + 3, y + 1, x + 3, y + 3), fill=ramp[0]); d.line((x, y + 3, x + 2, y + 3), fill=ramp[1])
    for y in range(4, 8):
        d.line((0, y, 3, y), fill=IRON[min(4, 7 - y)])
        d.line((4, y, 7, y), fill=FORGED[2 if y < 7 else 0])
        d.line((8, y, 11, y), fill=WOOD[3 if y % 2 else 4])
        d.line((12, y, 15, y), fill=BONE[3 if y % 2 else 1])
    d.point((5, 5), fill=BRONZE[3]); d.point((6, 5), fill=BRONZE[2])
    return im


# ---------------------------------------------------------------------------------------------- entity (rack sheets)

def entity_hide_raw():
    """The flesh side of a raw hide as it lies over the fleshing beam: pale with red flesh, furred rim."""
    n = 32
    mask = pelt_mask(n, 1.0)
    im, d = canvas((n, n))

    def inside(x, y):
        return 0 <= x < n and 0 <= y < n and mask[y][x]

    blobs = [(10, 11, 4.5), (21, 10, 4.0), (15, 18, 5.0), (8, 21, 3.5), (23, 21, 4.0)]
    for y in range(n):
        for x in range(n):
            if not mask[y][x]:
                continue
            rim = not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)))
            if rim:
                d.point((x, y), fill=FUR[1] if hash2(x, y, 12) > 100 else FUR[2])
                continue
            color = SCRAPED[3] if hash2(x, y, 13) > 40 else SCRAPED[2]
            for bx, by, br in blobs:
                dist = math.hypot(x + 0.5 - bx, (y + 0.5 - by) * 1.2)
                if dist < br:
                    color = FLESH[2] if dist < br * 0.6 else FLESH[3]
                    if hash2(x, y, 14) > 225:
                        color = FLESH[1]
            d.point((x, y), fill=color)
    return im


def entity_hide_leather():
    """A neutral, near-white leather sheet with grain; the renderer tints it per state and finish."""
    n = 32
    mask = pelt_mask(n, 1.0)
    im, d = canvas((n, n))

    def inside(x, y):
        return 0 <= x < n and 0 <= y < n and mask[y][x]

    for y in range(n):
        for x in range(n):
            if not mask[y][x]:
                continue
            rim = not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            h = hash2(x, y, 15)
            if rim:
                v = 150
            elif h < 36:
                v = 205
            elif h > 236:
                v = 255
            else:
                v = 232
            # Faint diagonal grain.
            if (x + 2 * y) % 7 == 0 and not rim:
                v -= 12
            d.point((x, y), fill=(v, v, v, 255))
    return im


def textures():
    out = {}
    out.update(hide_items())
    out['item/supple_leather.png'] = supple_leather()
    out['item/harness_leather.png'] = harness_leather()
    out['item/hardened_leather.png'] = hardened_leather()
    out['item/leather_scraps.png'] = scraps()
    out['item/binding_leather.png'] = binding_leather()
    out['item/bark.png'] = bark()
    out['item/tanners_knife.png'] = knife()
    out['item/awl.png'] = awl()
    out['block/worked_timber.png'] = worked_timber()
    out['block/timber_end.png'] = timber_end()
    out['block/tool_materials.png'] = tool_materials()
    out['block/vat_band.png'] = vat_band()
    out['block/vat_staves.png'] = vat_staves()
    out['block/vat_inner.png'] = vat_inner()
    out['block/bench_mat.png'] = bench_mat()
    out['block/rack_lashing.png'] = rack_lashing()
    out['entity/hide_raw.png'] = entity_hide_raw()
    out['entity/hide_leather.png'] = entity_hide_leather()
    return out
