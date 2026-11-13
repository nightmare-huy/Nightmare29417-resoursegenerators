#!/usr/bin/env python3
"""Generates the data-driven resources of the mod (pure Python 3, no third-party packages).

    python3 scripts/generate_resources.py

Writes into src/main/resources:
  * assets/pi_generator/textures/block/generator_tier_N{,_top,_bottom}.png   (16x16 animated strips, 16 frames each)
  * assets/pi_generator/textures/block/generator_tier_N{,_top,_bottom}.png.mcmeta
  * assets/pi_generator/{blockstates,models/block,models/item}/generator_tier_N.json
  * assets/pi_generator/textures/item/resource_filter.png (+ .mcmeta, animated), models/item/resource_filter.json
  * assets/pi_generator/{textures/item,models/item}/{speed,efficiency,energy}_upgrade_N.*
  * assets/pi_generator/textures/gui/{generator_tier_N,resource_filter}.png   (screen backgrounds, 256x256 sheets)
  * pi_generator_logo.png   (mods list logo)
  * data/pi_generator/loot_tables/blocks/generator_tier_N.json
  * data/pi_generator/recipes/*.json
  * data/minecraft/tags/blocks/{mineable/pickaxe,needs_iron_tool}.json

The language files (assets/pi_generator/lang/*.json) are maintained by hand.
"""
import colorsys
import json
import math
import os
import random
import struct
import zlib

MODID = "pi_generator"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", MODID)
DATA = os.path.join(ROOT, "data", MODID)
TIERS = range(1, 9)
SIZE = 16


def gen(tier):
    return f"{MODID}:generator_tier_{tier}"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# --------------------------------------------------------------------------- textures

# Accent colour per tier: grey -> green -> aqua -> blue -> yellow -> orange -> red -> (shimmering) purple
ACCENTS = {
    1: (0x9E, 0xA3, 0xA8),
    2: (0x4C, 0xC0, 0x5A),
    3: (0x2E, 0xC8, 0xD6),
    4: (0x3F, 0x6F, 0xE8),
    5: (0xF2, 0xC8, 0x30),
    6: (0xF2, 0x8C, 0x28),
    7: (0xE0, 0x3A, 0x3A),
    8: (0xB0, 0x4C, 0xF0),
}
STEEL = (0x6E, 0x72, 0x78)
FRAMES = 16  # animation frames per texture (stacked vertically in the PNG)
# Ticks per animation frame: the higher tiers pulse twice as fast.
FRAMETIME = {1: 4, 2: 4, 3: 4, 4: 4, 5: 2, 6: 2, 7: 2, 8: 2}
WHITE = (255, 255, 255)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k):
    return tuple(clamp(x * k) for x in c)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b) or - for images with transparency - (r, g, b, a) tuples."""
    channels = len(pixels[0][0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2 if channels == 3 else 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


def core_colour(tier, x, y, frame):
    """Colour of the glowing 6x6 core (x, y in 5..10)."""
    dx, dy = x - 7.5, y - 7.5
    dist = max(abs(dx), abs(dy))  # 0.5 (centre) .. 2.5 (edge)
    glow = 1.35 - 0.22 * (dist - 0.5)
    if tier == 8:
        # shimmering: the hue travels diagonally through purple / magenta / blue / cyan
        phase = (frame / FRAMES + (x + y) / 14.0) % 1.0
        hue = 0.62 + 0.30 * (0.5 + 0.5 * math.sin(phase * 2 * math.pi))
        r, g, b = colorsys.hsv_to_rgb(hue % 1.0, 0.62, 1.0)
        return mul((r * 255, g * 255, b * 255), 0.78 + 0.22 * (glow - 0.9) / 0.45)
    # tiers 1-7: a pulse that travels from the centre of the core to its edge
    ring = dist - 0.5  # 0 (centre), 1, 2 (edge)
    wave = 0.5 + 0.5 * math.cos(2 * math.pi * (frame / FRAMES - ring / 4.0))
    strength = 0.75 + 0.05 * tier  # higher tiers flare up more
    colour = mul(ACCENTS[tier], glow * (0.62 + 0.34 * wave * strength))
    return mix(colour, WHITE, 0.30 * wave * strength * (1.0 - ring / 2.5))


def texture_frame(tier, frame=0):
    accent = ACCENTS[tier]
    casing = mix(STEEL, accent, 0.30 if tier > 1 else 0.0)
    noise = random.Random(1000 + tier)  # same casing noise on every frame
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            n = noise.uniform(-0.05, 0.05)
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            if edge == 0:                                   # outer frame
                px = mul(casing, 0.42)
            elif edge == 1:                                 # bevel: light top/left, dark bottom/right
                px = mul(casing, 1.25 if (x == 1 or y == 1) and x != 14 and y != 14 else 0.68)
            else:
                px = mul(casing, 0.92 + n)                  # plate
            if (x in (2, 13)) and (y in (2, 13)):           # rivets
                px = mul(casing, 1.5)
            if 4 <= x <= 11 and 4 <= y <= 11:               # core socket + core
                px = mul(casing, 0.36)
                if 5 <= x <= 10 and 5 <= y <= 10:
                    px = core_colour(tier, x, y, frame)
            if y == 13 and 4 <= x <= 11:                    # tier pips: N of 8 lit
                lit = (x - 4) < tier
                # a highlight runs along the lit pips, left to right
                chase = max(0.0, math.cos(2 * math.pi * (frame / FRAMES - (x - 4) / 8.0)))
                px = mix(mul(accent, 0.95), WHITE, 0.45 * chase) if lit else mul(casing, 0.40)
                if tier == 8:
                    hue = 0.78 + 0.10 * math.sin(2 * math.pi * (frame / FRAMES - (x - 4) / 8.0))
                    r, g, b = colorsys.hsv_to_rgb(hue % 1.0, 0.55, 1.0)
                    px = (clamp(r * 255), clamp(g * 255), clamp(b * 255))
            row.append(px)
        rows.append(row)
    return rows


def casing_colour(tier):
    return mix(STEEL, ACCENTS[tier], 0.30 if tier > 1 else 0.0)


def casing_pixel(tier, x, y, noise):
    """Frame, bevel, plate and rivets shared by all faces. noise: one value per pixel, the same on every frame."""
    casing = casing_colour(tier)
    edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
    if edge == 0:                                       # outer frame
        px = mul(casing, 0.42)
    elif edge == 1:                                     # bevel: light top/left, dark bottom/right
        px = mul(casing, 1.25 if (x == 1 or y == 1) and x != 14 and y != 14 else 0.68)
    else:
        px = mul(casing, 0.92 + noise)                  # plate
    if (x in (2, 13)) and (y in (2, 13)):               # rivets
        px = mul(casing, 1.5)
    return px


def shimmer(phase, saturation=0.62):
    """Tier 8: purple / magenta / blue / cyan."""
    hue = 0.62 + 0.30 * (0.5 + 0.5 * math.sin(phase * 2 * math.pi))
    r, g, b = colorsys.hsv_to_rgb(hue % 1.0, saturation, 1.0)
    return (clamp(r * 255), clamp(g * 255), clamp(b * 255))


# The 20 cells around the 6x6 middle of a face, clockwise from the top left one: the path of the top face's light.
RING = ([(x, 5) for x in range(5, 11)] + [(10, y) for y in range(6, 11)]
        + [(x, 10) for x in range(9, 4, -1)] + [(5, y) for y in range(9, 5, -1)])


def top_frame(tier, frame=0):
    """Top face: an emitter. Two lights chase each other around a ring, the lens in the middle pulses."""
    accent = ACCENTS[tier]
    casing = casing_colour(tier)
    noise = random.Random(2000 + tier)
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            px = casing_pixel(tier, x, y, noise.uniform(-0.05, 0.05))
            # cooling slits on the four sides of the emitter
            if (y in (3, 12) and 5 <= x <= 10 and x % 2 == 1) or (x in (3, 12) and 5 <= y <= 10 and y % 2 == 1):
                px = mul(casing, 0.45)
            if 4 <= x <= 11 and 4 <= y <= 11:
                px = mul(casing, 0.36)                                      # socket
                dist = max(abs(x - 7.5), abs(y - 7.5))
                if (x, y) in RING:
                    position = RING.index((x, y)) / len(RING)
                    # two lights, half a turn apart, each with a fading tail
                    tail = ((frame / FRAMES - position) * 2) % 1.0
                    glow = max(0.0, 1.0 - tail * 1.6)
                    colour = shimmer(position + frame / FRAMES) if tier == 8 else accent
                    px = mix(mul(colour, 0.38), mix(colour, WHITE, 0.35), glow)
                elif dist == 1.5:                                           # dark gap between ring and lens
                    px = mul(casing, 0.26)
                elif dist == 0.5:                                           # lens
                    wave = 0.5 + 0.5 * math.cos(2 * math.pi * frame / FRAMES)
                    colour = shimmer(frame / FRAMES + (x + y) / 8.0, 0.45) if tier == 8 else accent
                    px = mix(mul(colour, 0.80), WHITE, 0.25 + 0.45 * wave)
            row.append(px)
        rows.append(row)
    return rows


def bottom_frame(tier, frame=0):
    """Bottom face: a base plate with four feet, a service hatch and one slowly blinking status light."""
    accent = ACCENTS[tier]
    casing = casing_colour(tier)
    noise = random.Random(3000 + tier)
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            px = casing_pixel(tier, x, y, noise.uniform(-0.05, 0.05))
            if (x in (3, 4, 11, 12)) and (y in (3, 4, 11, 12)):             # feet
                px = mul(casing, 0.30 if (x in (4, 12) or y in (4, 12)) else 0.40)
            if 5 <= x <= 10 and 5 <= y <= 10:                               # hatch
                hatch_edge = min(x - 5, y - 5, 10 - x, 10 - y)
                px = mul(casing, 0.50 if hatch_edge == 0 else 0.78)
                if hatch_edge >= 1 and y in (7, 9) and x < 9:               # label lines
                    px = mul(casing, 0.58)
                if (x, y) == (9, 6):                                        # status light
                    blink = 0.5 + 0.5 * math.cos(2 * math.pi * frame / FRAMES)
                    colour = shimmer(frame / FRAMES) if tier == 8 else accent
                    px = mix(mul(colour, 0.45), mix(colour, WHITE, 0.30), blink)
            row.append(px)
        rows.append(row)
    return rows


# Faces of a generator: texture name suffix -> function drawing one frame.
BLOCK_FACES = {"": texture_frame, "_top": top_frame, "_bottom": bottom_frame}


def generate_textures():
    tex_dir = os.path.join(ASSETS, "textures", "block")
    for tier in TIERS:
        for suffix, draw in BLOCK_FACES.items():
            path = os.path.join(tex_dir, f"generator_tier_{tier}{suffix}.png")
            rows = []
            for frame in range(FRAMES):
                rows.extend(draw(tier, frame))
            write_png(path, SIZE, SIZE * FRAMES, rows)
            write_json(path + ".mcmeta", {"animation": {"frametime": FRAMETIME[tier], "interpolate": True}})


FILTER_ART = [
    "................",
    ".oooooooooooooo.",
    ".ollllllllllllo.",
    ".omgmgmgmgmgmdo.",
    "..omgmgmgmgmdo..",
    "..ogmgmgmgmgdo..",
    "...ommmmmmmdo...",
    "...omPppppddo...",
    "....omPppddo....",
    "....omppppdo....",
    ".....ommddo.....",
    ".....ommddo.....",
    "......omdo......",
    "......omdo......",
    "......oooo......",
    "................",
]
FILTER_PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (0x26, 0x28, 0x2E, 255),   # outline
    "l": (0xD2, 0xD6, 0xDC, 255),   # rim highlight
    "m": (0x9A, 0x9F, 0xA6, 255),   # metal
    "d": (0x62, 0x66, 0x6E, 255),   # metal, shaded side
    "g": (0x3C, 0x40, 0x48, 255),   # mesh holes
    "p": (0xB0, 0x4C, 0xF0, 255),   # core
    "P": (0xDE, 0xA4, 0xFF, 255),   # core highlight
}


ITEM_FRAMES = 8      # animation frames of the item textures
ITEM_FRAMETIME = 3   # ticks per frame


def write_item_texture(name, draw):
    """draw(frame) -> 16 rows of RGBA pixels. Writes the frames as a vertical strip plus its .mcmeta."""
    path = os.path.join(ASSETS, "textures", "item", name + ".png")
    rows = []
    for frame in range(ITEM_FRAMES):
        rows.extend(draw(frame))
    write_png(path, SIZE, SIZE * ITEM_FRAMES, rows)
    write_json(path + ".mcmeta", {"animation": {"frametime": ITEM_FRAMETIME, "interpolate": True}})


def filter_texture(frame=0):
    """The core in the funnel glows: a pulse runs down through it."""
    rows = []
    for y, line in enumerate(FILTER_ART):
        row = []
        for c in line:
            px = FILTER_PALETTE[c]
            if c in "pP":
                wave = 0.5 + 0.5 * math.cos(2 * math.pi * (frame / ITEM_FRAMES - (y - 7) / 4.0))
                px = mix(mul(px[:3], 0.80), WHITE, 0.10 + 0.40 * wave) + (255,)
            row.append(px)
        rows.append(row)
    return rows


def generate_filter_item():
    assert len(FILTER_ART) == SIZE and all(len(row) == SIZE for row in FILTER_ART), FILTER_ART
    write_item_texture("resource_filter", filter_texture)
    write_json(os.path.join(ASSETS, "models", "item", "resource_filter.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/resource_filter"}})


# Upgrades: a card with the symbol of the upgrade kind, a border in the tier colour and N of 4 tier pips.
UPGRADE_TIERS = range(1, 5)
UPGRADE_SYMBOLS = {
    "speed": ((0xFF, 0x5A, 0x3C), ["#...#...",
                                   ".#...#..",
                                   "..#...#.",
                                   "..#...#.",
                                   ".#...#..",
                                   "#...#..."]),
    "efficiency": ((0x4C, 0xD0, 0x6A), ["...##...",
                                        "...##...",
                                        "########",
                                        "########",
                                        "...##...",
                                        "...##..."]),
    "energy": ((0xFF, 0xD2, 0x30), ["....##..",
                                    "...##...",
                                    "..#####.",
                                    "....##..",
                                    "...##...",
                                    "..##...."]),
}
UPGRADE_BORDERS = {1: (0xB8, 0xBC, 0xC2), 2: (0xF0, 0xC0, 0x40), 3: (0x5C, 0xE0, 0xE8), 4: (0xB0, 0x4C, 0xF0)}
UPGRADE_OUTLINE = (0x1E, 0x20, 0x26)
UPGRADE_PANEL = (0x2C, 0x2F, 0x38)


def upgrade_texture(kind, tier, frame=0):
    """A highlight sweeps over the symbol from left to right and runs along the lit tier pips."""
    colour, symbol = UPGRADE_SYMBOLS[kind]
    border = UPGRADE_BORDERS[tier]
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            corner = (x in (1, 14)) and (y in (1, 14))
            if edge == 0 or corner:
                px = None                                           # transparent
            elif edge == 1:
                px = UPGRADE_OUTLINE
            elif edge == 2:
                px = mul(border, 1.0 if (x == 2 or y == 2) else 0.72)   # lit top/left, shaded bottom/right
            else:
                px = UPGRADE_PANEL
                sweep = max(0.0, math.cos(2 * math.pi * (frame / ITEM_FRAMES - (x - 4) / 10.0)))
                if 4 <= x <= 11 and 3 <= y <= 8 and symbol[y - 3][x - 4] == "#":
                    px = mix(colour, WHITE, 0.35) if y <= 5 else colour
                    px = mix(mul(px, 0.85), WHITE, 0.45 * sweep)
                if y in (10, 11) and x in (4, 6, 8, 10):                # tier pips
                    px = mix(mul(border, 0.85), WHITE, 0.40 * sweep) if (x - 4) // 2 < tier else mul(UPGRADE_PANEL, 0.6)
            row.append(px + (255,) if px else (0, 0, 0, 0))
        rows.append(row)
    return rows


def generate_upgrade_items():
    for kind in UPGRADE_SYMBOLS:
        for tier in UPGRADE_TIERS:
            name = f"{kind}_upgrade_{tier}"
            write_item_texture(name, lambda frame, kind=kind, tier=tier: upgrade_texture(kind, tier, frame))
            write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})


# --------------------------------------------------------------------------- screens (GUI backgrounds)

# The two screens use 256x256 sheets in the vanilla container style. The numbers below must match the slot
# positions in GeneratorMenu / FilterMenu and the bar positions in GeneratorScreen.
GUI_SHEET = 256
GUI_WIDTH = 176
GUI_FACE = (0xC6, 0xC6, 0xC6)
GUI_LIGHT = (0xFF, 0xFF, 0xFF)
GUI_SHADOW = (0x55, 0x55, 0x55)
GUI_BLACK = (0x00, 0x00, 0x00)
GUI_SLOT = (0x8B, 0x8B, 0x8B)
GUI_SLOT_DARK = (0x37, 0x37, 0x37)
GUI_HINT = (0x70, 0x70, 0x70)
GUI_BAR_EMPTY = (0x2A, 0x10, 0x10)
GUI_PROGRESS_EMPTY = (0x5A, 0x5A, 0x5A)

GENERATOR_GUI_HEIGHT = 222
GENERATOR_ENERGY_BAR = (9, 18, 14, 48)       # x, y, width, height of the inside of the energy bar
GENERATOR_DISPLAY = (29, 17, 140, 32)        # dark "screen" with the three text lines
GENERATOR_PIPS = (143, 41)                   # tier pips in the bottom right corner of the display: 8 x (2x3), 1 apart
GENERATOR_PROGRESS_BAR = (31, 54, 60, 10)
GENERATOR_INSTALLED_SLOTS = (98, 50)         # filter, speed, efficiency, energy
GENERATOR_BUFFER_Y = 72
GENERATOR_INVENTORY_Y = 140
GENERATOR_ENERGY_FILL_UV = (176, 0)          # sprites drawn over the empty bars
GENERATOR_PROGRESS_FILL_UV = (176, 48)
GUI_DISPLAY = (0x1E, 0x21, 0x28)
GUI_DISPLAY_OFF = (0x34, 0x37, 0x3F)

FILTER_GUI_HEIGHT = 174
FILTER_GHOST_Y = 18
FILTER_INVENTORY_Y = 92
FILTER_GHOST_SLOT = (0x7C, 0x84, 0x96)       # ghost slots are tinted so that they do not look like storage
# Mode button: 160x20 sprites below the window: except, except (hovered), only, only (hovered).
FILTER_BUTTON_SIZE = (160, 20)
FILTER_BUTTON_V = 174
FILTER_MODE_COLOURS = {"except": (0xF0, 0xA0, 0x30), "only": (0x4C, 0xDC, 0xE6)}   # as in the tooltips: gold / aqua
FILTER_MODE_ICONS = {
    "except": ["##.....##",
               ".##...##.",
               "..##.##..",
               "...###...",
               "..##.##..",
               ".##...##.",
               "##.....##"],
    "only": [".........##",
             "........##.",
             ".......##..",
             "##....##...",
             ".##..##....",
             "..####.....",
             "...##......"],
}


class Sheet:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * GUI_SHEET for _ in range(GUI_SHEET)]

    def set(self, x, y, colour):
        self.px[y][x] = tuple(colour) + (255,)

    def rect(self, x, y, w, h, colour):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, colour)

    def panel(self, w, h):
        """Vanilla style window: black outline with cut corners, light top/left, shaded bottom/right."""
        self.rect(0, 0, w, h, GUI_FACE)
        self.rect(1, 1, w - 3, 2, GUI_LIGHT)
        self.rect(1, 1, 2, h - 3, GUI_LIGHT)
        self.rect(3, h - 3, w - 4, 2, GUI_SHADOW)
        self.rect(w - 3, 3, 2, h - 4, GUI_SHADOW)
        self.set(3, 3, GUI_LIGHT)
        self.set(w - 4, h - 4, GUI_SHADOW)
        self.rect(2, 0, w - 4, 1, GUI_BLACK)
        self.rect(2, h - 1, w - 4, 1, GUI_BLACK)
        self.rect(0, 2, 1, h - 4, GUI_BLACK)
        self.rect(w - 1, 2, 1, h - 4, GUI_BLACK)
        for cx, cy in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            self.set(cx, cy, GUI_BLACK)
        for cx, cy in ((0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1),
                       (0, h - 1), (1, h - 1), (0, h - 2), (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)):
            self.px[cy][cx] = (0, 0, 0, 0)
        self.set(w - 3, 2, GUI_FACE)
        self.set(2, h - 3, GUI_FACE)

    def inset(self, x, y, w, h, inside):
        """Sunken frame around the inner area x, y, w, h (the frame itself lies one pixel outside of it)."""
        self.rect(x - 1, y - 1, w + 1, 1, GUI_SLOT_DARK)
        self.rect(x - 1, y - 1, 1, h + 1, GUI_SLOT_DARK)
        self.rect(x, y + h, w + 1, 1, GUI_LIGHT)
        self.rect(x + w, y, 1, h + 1, GUI_LIGHT)
        self.set(x + w, y - 1, GUI_SLOT)
        self.set(x - 1, y + h, GUI_SLOT)
        self.rect(x, y, w, h, inside)

    def slot(self, x, y, inside=GUI_SLOT):
        """x, y: where the item is drawn (as in Slot.x / Slot.y)."""
        self.inset(x, y, 16, 16, inside)

    def slots(self, x, y, columns, rows, inside=GUI_SLOT):
        for row in range(rows):
            for column in range(columns):
                self.slot(x + column * 18, y + row * 18, inside)

    def inventory(self, y):
        self.slots(8, y, 9, 3)
        self.slots(8, y + 58, 9, 1)

    def mask(self, x, y, rows, colour, skip="."):
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c not in skip:
                    self.set(x + dx, y + dy, colour)

    def save(self, name):
        write_png(os.path.join(ASSETS, "textures", "gui", name + ".png"), GUI_SHEET, GUI_SHEET, self.px)


def generator_sheet(tier):
    accent = ACCENTS[tier]
    sheet = Sheet()
    sheet.panel(GUI_WIDTH, GENERATOR_GUI_HEIGHT)
    x, y, w, h = GENERATOR_ENERGY_BAR
    sheet.inset(x, y, w, h, GUI_BAR_EMPTY)
    for line in range(y + 3, y + h, 4):                       # segment marks
        sheet.rect(x, line, w, 1, mul(GUI_BAR_EMPTY, 0.6))

    # display: dark screen with a border in the tier colour and the tier pips
    x, y, w, h = GENERATOR_DISPLAY
    sheet.inset(x, y, w, h, mul(accent, 0.62))
    sheet.rect(x + 1, y + 1, w - 2, h - 2, GUI_DISPLAY)
    px, py = GENERATOR_PIPS
    for pip in range(8):
        colour = (shimmer(pip / 8.0, 0.55) if tier == 8 else accent) if pip < tier else GUI_DISPLAY_OFF
        sheet.rect(px + pip * 3, py, 2, 3, colour)

    x, y, w, h = GENERATOR_PROGRESS_BAR
    sheet.inset(x, y, w, h, GUI_DISPLAY)

    x, y = GENERATOR_INSTALLED_SLOTS
    sheet.slots(x, y, 4, 1)
    sheet.mask(x, y, FILTER_ART, GUI_HINT, skip=".lmdgpP")    # what belongs into each slot: outlines only
    for index, kind in enumerate(UPGRADE_SYMBOLS):
        sheet.mask(x + 18 * (index + 1) + 4, y + 5, UPGRADE_SYMBOLS[kind][1], GUI_HINT)
    sheet.slots(8, GENERATOR_BUFFER_Y, 9, 3)
    sheet.inventory(GENERATOR_INVENTORY_Y)

    # fill of the energy bar: red at the bottom to yellow at the top, with the same segment marks
    u, v = GENERATOR_ENERGY_FILL_UV
    _, _, w, h = GENERATOR_ENERGY_BAR
    for dy in range(h):
        colour = mix((0xE8, 0x2A, 0x1E), (0xFF, 0xC8, 0x30), 1.0 - dy / (h - 1))
        for dx in range(w):
            shade = 1.15 if dx in (1, 2) else 0.8 if dx >= w - 2 else 1.0
            if dy % 4 == 3:
                shade *= 0.7
            sheet.set(u + dx, v + dy, mul(colour, shade))
    # fill of the progress bar, in the tier colour
    u, v = GENERATOR_PROGRESS_FILL_UV
    _, _, w, h = GENERATOR_PROGRESS_BAR
    for dy in range(h):
        shade = 1.25 if dy in (1, 2) else 0.70 if dy >= h - 2 else 1.0
        for dx in range(w):
            colour = shimmer(dx / w * 0.5 + 0.5, 0.55) if tier == 8 else accent
            sheet.set(u + dx, v + dy, mul(colour, shade * (0.88 if dx % 6 == 5 else 1.0)))
    return sheet


def filter_button(sheet, v, mode, hovered):
    """One state of the mode button: a dark plate tinted in the mode colour with the mode icon on the left."""
    w, h = FILTER_BUTTON_SIZE
    colour = FILTER_MODE_COLOURS[mode]
    plate = mix(UPGRADE_PANEL, colour, 0.34 if hovered else 0.20)
    sheet.rect(0, v, w, h, GUI_LIGHT if hovered else GUI_BLACK)                 # outline
    sheet.rect(1, v + 1, w - 2, h - 2, mul(plate, 0.62))                        # shaded bottom / right
    sheet.rect(1, v + 1, w - 3, h - 3, mix(plate, WHITE, 0.30))                 # lit top / left
    sheet.rect(2, v + 2, w - 4, h - 4, plate)
    icon = FILTER_MODE_ICONS[mode]
    ix, iy = 4 + (12 - len(icon[0])) // 2, v + (h - len(icon)) // 2
    sheet.mask(ix + 1, iy + 1, icon, mul(plate, 0.45))                          # drop shadow
    sheet.mask(ix, iy, icon, mix(colour, WHITE, 0.25 if hovered else 0.0))
    sheet.rect(19, v + 3, 1, h - 6, mul(plate, 0.62))                           # separator
    sheet.rect(20, v + 3, 1, h - 6, mix(plate, WHITE, 0.18))


def generate_gui():
    for tier in TIERS:
        generator_sheet(tier).save(f"generator_tier_{tier}")

    sheet = Sheet()
    sheet.panel(GUI_WIDTH, FILTER_GUI_HEIGHT)
    sheet.slots(8, FILTER_GHOST_Y, 9, 2, FILTER_GHOST_SLOT)
    sheet.inventory(FILTER_INVENTORY_Y)
    v = FILTER_BUTTON_V
    for mode in ("except", "only"):
        for hovered in (False, True):
            filter_button(sheet, v, mode, hovered)
            v += FILTER_BUTTON_SIZE[1]
    sheet.save("resource_filter")


# --------------------------------------------------------------------------- mod logo

def generate_logo():
    """Logo shown in the mods list (logoFile in mods.toml): the tier 8 generator, 8x enlarged."""
    frame, scale = texture_frame(8, 0), 8
    write_png(os.path.join(ROOT, "pi_generator_logo.png"), SIZE * scale, SIZE * scale,
              [[px for px in row for _ in range(scale)] for row in frame for _ in range(scale)])


# --------------------------------------------------------------------------- models / blockstates / loot

def generate_models():
    for tier in TIERS:
        name = f"generator_tier_{tier}"
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                   {"variants": {"": {"model": f"{MODID}:block/{name}"}}})
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
                   {"parent": "minecraft:block/cube_bottom_top", "textures": {
                       "top": f"{MODID}:block/{name}_top",
                       "bottom": f"{MODID}:block/{name}_bottom",
                       "side": f"{MODID}:block/{name}"}})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": f"{MODID}:block/{name}"})


def generate_loot_and_tags():
    for tier in TIERS:
        name = f"generator_tier_{tier}"
        write_json(os.path.join(DATA, "loot_tables", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": gen(tier)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
            "random_sequence": f"{MODID}:blocks/{name}",
        })
    values = [gen(t) for t in TIERS]
    tags = os.path.join(ROOT, "data", "minecraft", "tags", "blocks")
    write_json(os.path.join(tags, "mineable", "pickaxe.json"), {"replace": False, "values": values})
    write_json(os.path.join(tags, "needs_iron_tool.json"), {"replace": False, "values": values})


# --------------------------------------------------------------------------- recipes

def item(item_id):
    return {"item": item_id}


def shaped(pattern, key, tier):
    """Vanilla 3x3 crafting table recipe."""
    return {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {k: item(v) for k, v in key.items()},
        "pattern": pattern,
        "result": item(gen(tier)),
        "show_notification": True,
    }


def extreme(pattern, key, tier):
    """Re-Avaritia 1.20.1 extreme crafting table recipe (9x9 grid = table tier 4)."""
    assert len(pattern) == 9 and all(len(row) == 9 for row in pattern), pattern
    used = set("".join(pattern)) - {" "}
    assert used == set(key), (used, set(key))
    return {
        "type": "avaritia:shaped_table",
        "category": "misc",
        "key": {k: item(v) for k, v in key.items()},
        "pattern": pattern,
        "result": item(gen(tier)),
        "show_notification": True,
        "tier": 4,
    }


def conditional(*variants):
    """forge:conditional - the first variant whose conditions all pass is loaded, otherwise the recipe is skipped."""
    return {
        "type": "forge:conditional",
        "recipes": [{"conditions": conditions, "recipe": recipe} for conditions, recipe in variants],
    }


EXTREME_TABLE = {"type": f"{MODID}:recipe_serializer_exists", "serializer": "avaritia:shaped_table"}
FALLBACK = {"type": f"{MODID}:fallback_recipes"}


def exists(item_id):
    return {"type": "forge:item_exists", "item": item_id}


# Avaritia item ids. Re-Avaritia (1.20.1) uses "neutron_ingot" / "neutron" for the neutronium ingot / block,
# other ports call them "neutronium_ingot" / "neutronium_block" - both spellings get a recipe variant.
NEUTRONIUM_NAMES = [
    {"ingot": "avaritia:neutron_ingot", "block": "avaritia:neutron"},
    {"ingot": "avaritia:neutronium_ingot", "block": "avaritia:neutronium_block"},
]


def generate_filter_recipes():
    out = os.path.join(DATA, "recipes")
    filter_id = f"{MODID}:resource_filter"
    write_json(os.path.join(out, "resource_filter.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"I": item("minecraft:iron_ingot"), "P": item("minecraft:paper"), "H": item("minecraft:hopper")},
        "pattern": ["IPI",
                    "PHP",
                    "IPI"],
        "result": item(filter_id),
        "show_notification": True,
    })
    # Crafting a filter by itself returns a blank one (the result carries no NBT).
    write_json(os.path.join(out, "resource_filter_clear.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item(filter_id)],
        "result": item(filter_id),
    })


# Upgrade recipes: tier 1 around a basic control circuit, every next tier around the previous upgrade.
UPGRADE_KIND_ITEM = {"speed": "minecraft:redstone_block", "efficiency": "minecraft:lapis_block",
                     "energy": "minecraft:glowstone"}
UPGRADE_TIER_ITEM = {1: "minecraft:iron_ingot", 2: "mekanism:alloy_infused", 3: "mekanism:alloy_reinforced",
                     4: "mekanism:alloy_atomic"}


def generate_upgrade_recipes():
    out = os.path.join(DATA, "recipes")
    for kind, kind_item in UPGRADE_KIND_ITEM.items():
        for tier in UPGRADE_TIERS:
            name = f"{kind}_upgrade_{tier}"
            centre = "mekanism:basic_control_circuit" if tier == 1 else f"{MODID}:{kind}_upgrade_{tier - 1}"
            write_json(os.path.join(out, name + ".json"), {
                "type": "minecraft:crafting_shaped",
                "category": "misc",
                "key": {"M": item(UPGRADE_TIER_ITEM[tier]), "K": item(kind_item), "C": item(centre)},
                "pattern": ["MKM",
                            "KCK",
                            "MKM"],
                "result": item(f"{MODID}:{name}"),
                "show_notification": True,
            })


def generate_recipes():
    out = os.path.join(DATA, "recipes")

    # ---- tier 1-5: crafting table --------------------------------------------------------------
    write_json(os.path.join(out, "generator_tier_1.json"), shaped(
        ["ICI",
         "OFO",
         "IRI"],
        {"F": "thermal:machine_frame", "I": "thermal:invar_ingot", "C": "mekanism:basic_control_circuit",
         "R": "thermal:rf_coil", "O": "mekanism:ingot_osmium"}, 1))

    write_json(os.path.join(out, "generator_tier_2.json"), shaped(
        ["ECE",
         "AGA",
         "ETE"],
        {"G": gen(1), "E": "thermal:electrum_ingot", "A": "mekanism:alloy_infused",
         "C": "mekanism:advanced_control_circuit", "T": "mekanism:energy_tablet"}, 2))

    write_json(os.path.join(out, "generator_tier_3.json"), shaped(
        ["SPS",
         "AGA",
         "SFS"],
        {"G": gen(2), "F": "ae2:fluix_block", "P": "ae2:engineering_processor",
         "S": "thermal:signalum_ingot", "A": "mekanism:alloy_reinforced"}, 3))

    write_json(os.path.join(out, "generator_tier_4.json"), shaped(
        ["ECE",
         "AGA",
         "KIK"],
        {"G": gen(3), "E": "thermal:enderium_ingot", "K": "ae2:cell_component_64k",
         "A": "mekanism:alloy_atomic", "C": "mekanism:elite_control_circuit", "I": "ae2:interface"}, 4))

    write_json(os.path.join(out, "generator_tier_5.json"), shaped(
        ["PCP",
         "QGQ",
         "PSP"],
        {"G": gen(4), "C": "mekanism:ultimate_control_circuit", "P": "mekanism:pellet_polonium",
         "S": "ae2:singularity", "Q": "ae2:quantum_ring"}, 5))

    # ---- tier 6-8: Avaritia extreme crafting table (9x9) ---------------------------------------
    # plus 3x3 fallbacks that are only enabled when the 9x9 recipe type is unavailable
    # (or when forceFallbackRecipes=true in config/pi_generator-common.toml).

    # tier 6
    write_json(os.path.join(out, "generator_tier_6.json"), conditional(
        ([EXTREME_TABLE], extreme(
            ["CCCPPPCCC",
             "CKPPCPPKC",
             "CPCCCCCPC",
             "PPCKPKCPP",
             "PCCPGPCCP",
             "PPCKPKCPP",
             "CPCCCCCPC",
             "CKPPCPPKC",
             "CCCPPPCCC"],
            {"G": gen(5), "P": "mekanism:pellet_plutonium", "C": "avaritia:crystal_matrix_ingot",
             "K": "ae2:cell_component_256k"}, 6))))
    write_json(os.path.join(out, "generator_tier_6_fallback.json"), conditional(
        ([FALLBACK, exists("avaritia:crystal_matrix_ingot")], shaped(
            ["CPC",
             "KGK",
             "CPC"],
            {"G": gen(5), "P": "mekanism:pellet_plutonium", "C": "avaritia:crystal_matrix_ingot",
             "K": "ae2:cell_component_256k"}, 6))))

    # tier 7
    write_json(os.path.join(out, "generator_tier_7.json"), conditional(*[
        ([EXTREME_TABLE, exists(names["ingot"])], extreme(
            ["NNNNANNNN",
             "NSAAAAASN",
             "NANNNNNAN",
             "NANSASNAN",
             "AANAGANAA",
             "NANSASNAN",
             "NANNNNNAN",
             "NSAAAAASN",
             "NNNNANNNN"],
            {"G": gen(6), "A": "mekanism:pellet_antimatter", "N": names["ingot"], "S": "ae2:singularity"}, 7))
        for names in NEUTRONIUM_NAMES]))
    write_json(os.path.join(out, "generator_tier_7_fallback.json"), conditional(*[
        ([FALLBACK, exists(names["ingot"])], shaped(
            ["NAN",
             "SGS",
             "NAN"],
            {"G": gen(6), "A": "mekanism:pellet_antimatter", "N": names["ingot"], "S": "ae2:singularity"}, 7))
        for names in NEUTRONIUM_NAMES]))

    # tier 8
    write_json(os.path.join(out, "generator_tier_8.json"), conditional(*[
        ([EXTREME_TABLE, exists(names["block"])], extreme(
            ["NNNIIINNN",
             "NAAICIAAN",
             "NAIIIIIAN",
             "IIICICIII",
             "ICIIGIICI",
             "IIICICIII",
             "NAIIIIIAN",
             "NAAICIAAN",
             "NNNIIINNN"],
            {"G": gen(7), "I": "avaritia:infinity_ingot", "C": "avaritia:infinity_catalyst",
             "N": names["block"], "A": "mekanism:pellet_antimatter"}, 8))
        for names in NEUTRONIUM_NAMES]))
    write_json(os.path.join(out, "generator_tier_8_fallback.json"), conditional(*[
        ([FALLBACK, exists(names["block"])], shaped(
            ["NCN",
             "IGI",
             "ACA"],
            {"G": gen(7), "I": "avaritia:infinity_ingot", "C": "avaritia:infinity_catalyst",
             "N": names["block"], "A": "mekanism:pellet_antimatter"}, 8))
        for names in NEUTRONIUM_NAMES]))


if __name__ == "__main__":
    generate_textures()
    generate_filter_item()
    generate_upgrade_items()
    generate_gui()
    generate_logo()
    generate_models()
    generate_loot_and_tags()
    generate_recipes()
    generate_filter_recipes()
    generate_upgrade_recipes()
    print("resources written to", os.path.normpath(ROOT))
