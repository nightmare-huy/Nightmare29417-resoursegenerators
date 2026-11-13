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
  * assets/pi_generator/{textures/item,models/item}/{crystal,neutron,infinity}_core.*
  * energy storage block: assets/pi_generator/textures/block/energy_storage_{input,output,off}.png (+ .mcmeta),
    models/block/energy_storage_{input,output,off}.json (one face each), blockstates/energy_storage.json
    (multipart: the face of every side follows its mode), models/item/energy_storage.json,
    textures/gui/energy_storage.png, data/pi_generator/loot_table/blocks/energy_storage.json
  * data/pi_generator/loot_table/blocks/generator_tier_N.json
  * data/pi_generator/recipe/*.json   (the folder is emptied first)
  * data/minecraft/tags/block/{mineable/pickaxe,needs_iron_tool}.json

Folder names and the recipe format are those of Minecraft 1.21.1 / NeoForge.

The language files (assets/pi_generator/lang/*.json) are maintained by hand.
"""
import colorsys
import json
import math
import os
import random
import shutil
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


# --------------------------------------------------------------------------- energy storage block
#
# One texture per side mode. A face is the same seen from all four directions, so it can be turned freely
# by the block state file. Around the glowing cell in the middle there is one arrow per edge: pointing to
# the cell (input), away from it (output) or missing (side switched off, the cell is dark).

ENERGY_STORAGE = f"{MODID}:energy_storage"
STORAGE_CASING = (0x4E, 0x52, 0x62)
STORAGE_CELL = (0xFF, 0xD8, 0x4A)
STORAGE_MODES = {"input": (0x4C, 0xDC, 0xE6), "output": (0xF0, 0xA0, 0x30), "off": None}   # aqua / gold, as in the texts
STORAGE_FRAMETIME = 2


def storage_arrow(x, y):
    """Is (x, y) part of an arrow? -> (distance from the edge: 0 outer row / 1 inner row, wide row?) or None.
    Each arrow is two rows in the band between the frame and the cell; 'wide' is the 4 pixel row."""
    for turn in range(4):                       # look at the pixel as if its edge was the top one
        if y in (2, 3) and 6 <= x <= 9:
            return y - 2, x
        x, y = y, SIZE - 1 - x
    return None


def storage_frame(mode, frame=0):
    colour = STORAGE_MODES[mode]
    noise = random.Random(4000)
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            n = noise.uniform(-0.05, 0.05)
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            if edge == 0:                                                   # outer frame
                px = mul(STORAGE_CASING, 0.42)
            elif edge == 1:                                                 # bevel
                px = mul(STORAGE_CASING, 1.25 if (x == 1 or y == 1) and x != 14 and y != 14 else 0.68)
            else:
                px = mul(STORAGE_CASING, 0.92 + n)
            if (x in (2, 13)) and (y in (2, 13)):                           # rivets: lit in the mode colour
                px = mul(STORAGE_CASING, 1.5) if colour is None else mix(mul(STORAGE_CASING, 1.5), colour, 0.55)
            arrow = storage_arrow(x, y)
            if arrow is not None and colour is not None:
                depth, along = arrow
                # input: wide row outside, tip towards the cell; output: the other way round
                wide = depth == (0 if mode == "input" else 1)
                if wide or along in (7, 8):
                    # a pulse runs along the arrow in the direction the energy flows
                    position = depth if mode == "input" else 1 - depth
                    wave = 0.5 + 0.5 * math.cos(2 * math.pi * (frame / FRAMES - position / 2.0))
                    px = mix(mul(colour, 0.55), mix(colour, WHITE, 0.45), wave)
            if 4 <= x <= 11 and 4 <= y <= 11:                               # socket + cell
                px = mul(STORAGE_CASING, 0.36)
                if 5 <= x <= 10 and 5 <= y <= 10:
                    dist = max(abs(x - 7.5), abs(y - 7.5))                  # 0.5 (centre) .. 2.5 (edge)
                    if colour is None:
                        px = mul(STORAGE_CASING, 0.62 - 0.08 * (dist - 0.5))   # switched off: dark glass
                        if (x, y) in ((6, 6), (7, 6), (6, 7)):
                            px = mul(STORAGE_CASING, 0.85)                  # reflection
                    else:
                        # plasma: two waves crossing each other, brightest in the middle
                        wave = (0.5 + 0.25 * math.sin(2 * math.pi * (frame / FRAMES + (x + y) / 9.0))
                                + 0.25 * math.sin(2 * math.pi * (2 * frame / FRAMES - (x - y) / 7.0)))
                        glow = 1.25 - 0.20 * (dist - 0.5)
                        px = mix(mul(STORAGE_CELL, glow * (0.70 + 0.30 * wave)), WHITE,
                                 0.55 * wave * (1.0 - (dist - 0.5) / 2.5))
            row.append(px)
        rows.append(row)
    return rows


def generate_energy_storage():
    tex_dir = os.path.join(ASSETS, "textures", "block")
    name = "energy_storage"
    for mode in STORAGE_MODES:
        path = os.path.join(tex_dir, f"{name}_{mode}.png")
        if mode == "off":                                   # nothing moves on a switched off side
            write_png(path, SIZE, SIZE, storage_frame(mode))
        else:
            rows = []
            for frame in range(FRAMES):
                rows.extend(storage_frame(mode, frame))
            write_png(path, SIZE, SIZE * FRAMES, rows)
            write_json(path + ".mcmeta", {"animation": {"frametime": STORAGE_FRAMETIME, "interpolate": True}})
        # one face (north) of a full cube; the block state file turns it to the side it belongs to
        texture = f"{MODID}:block/{name}_{mode}"
        write_json(os.path.join(ASSETS, "models", "block", f"{name}_{mode}.json"), {
            "ambientocclusion": True,
            "textures": {"face": texture, "particle": texture},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                          "faces": {"north": {"texture": "#face", "cullface": "north"}}}],
        })
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"multipart": [
        {"when": {side: mode}, "apply": {"model": f"{MODID}:block/{name}_{mode}", **turn}}
        for side, turn in turns.items() for mode in STORAGE_MODES]})
    # the item: an output at the front, inputs elsewhere
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {
        "parent": "minecraft:block/cube",
        "textures": {"particle": f"{MODID}:block/{name}_input",
                     **{side: f"{MODID}:block/{name}_{'output' if side == 'north' else 'input'}" for side in turns}}})
    # dropped with the energy inside
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": ENERGY_STORAGE, "functions": [
                {"function": "minecraft:copy_components", "source": "block_entity", "include": [f"{MODID}:energy"]}]}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"{MODID}:blocks/{name}",
    })


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


# Cores (crafting components of the tier 6-8 generators without Avaritia): a glowing sphere held by four
# corner brackets. Sphere colour (None = shimmering like the tier 8 generator) and bracket colour.
CORES = {
    "crystal_core": ((0x58, 0xE0, 0xEC), ACCENTS[6]),
    "neutron_core": ((0x8E, 0x94, 0x9E), ACCENTS[7]),
    "infinity_core": (None, ACCENTS[8]),
}
CORE_OUTLINE = (0x1E, 0x20, 0x26)


def core_texture(name, frame=0):
    """A pulse runs from the centre of the sphere to its rim."""
    colour, bracket = CORES[name]
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            dx, dy = x - 7.5, y - 7.5
            distance = math.hypot(dx, dy)
            px = None
            if distance <= 5.3:
                base = colour or shimmer((frame / ITEM_FRAMES + (x + y) / 24.0) % 1.0)
                light = max(0.0, 1.0 - math.hypot(dx + 2.0, dy + 2.0) / 7.5)          # lit from the top left
                pulse = 0.5 + 0.5 * math.cos(2 * math.pi * (frame / ITEM_FRAMES - distance / 6.0))
                px = mix(mul(base, 0.50 + 0.55 * light), WHITE, 0.45 * pulse * (0.35 + 0.65 * light))
            elif distance <= 6.4:
                px = CORE_OUTLINE
            near_x, near_y = min(x, SIZE - 1 - x), min(y, SIZE - 1 - y)
            if px is None and ((near_y == 0 and near_x <= 3) or (near_x == 0 and near_y <= 3)):
                px = mul(bracket, 1.0 if (x == 0 or y == 0) else 0.65)                # lit top/left, shaded bottom/right
            row.append(px + (255,) if px else (0, 0, 0, 0))
        rows.append(row)
    return rows


def generate_core_items():
    for name in CORES:
        write_item_texture(name, lambda frame, name=name: core_texture(name, frame))
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

ENERGY_STORAGE_GUI_HEIGHT = 130
ENERGY_STORAGE_DISPLAY = (9, 18, 158, 34)    # dark "screen" with the three text lines
ENERGY_STORAGE_BUTTONS = (7, 58, 80, 20, 2)  # x, y, width, height, gap: 2 columns x 3 rows of vanilla buttons

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

    # energy storage: only a display, the six side buttons are vanilla widgets drawn by the screen
    sheet = Sheet()
    sheet.panel(GUI_WIDTH, ENERGY_STORAGE_GUI_HEIGHT)
    x, y, w, h = ENERGY_STORAGE_DISPLAY
    sheet.inset(x, y, w, h, mul(STORAGE_CELL, 0.62))
    sheet.rect(x + 1, y + 1, w - 2, h - 2, GUI_DISPLAY)
    sheet.save("energy_storage")


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
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": gen(tier)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
            "random_sequence": f"{MODID}:blocks/{name}",
        })
    values = [gen(t) for t in TIERS] + [ENERGY_STORAGE]
    tags = os.path.join(ROOT, "data", "minecraft", "tags", "block")
    write_json(os.path.join(tags, "mineable", "pickaxe.json"), {"replace": False, "values": values})
    write_json(os.path.join(tags, "needs_iron_tool.json"), {"replace": False, "values": values})


# --------------------------------------------------------------------------- recipes
#
# Minecraft 1.21.1 format: results are {"id": ..., "count": ...}; a recipe file may start with a
# "neoforge:conditions" list and is skipped without an error when one of the conditions fails.
# Every recipe is only loaded when the mods its ingredients come from are installed.

RECIPES = os.path.join(DATA, "recipe")


def item(item_id):
    return {"item": item_id}


def result(item_id, count=1):
    return {"count": count, "id": item_id}


def mod_loaded(modid):
    return {"type": "neoforge:mod_loaded", "modid": modid}


def exists(item_id):
    return {"type": "neoforge:item_exists", "item": item_id}


# true when the recipe type of the Avaritia extreme crafting table is registered
EXTREME_TABLE = {"type": f"{MODID}:recipe_serializer_exists", "serializer": "avaritia:shaped_table"}
# follows recipes.coreRecipes in config/pi_generator-common.toml (default: always true)
CORE_RECIPES = {"type": f"{MODID}:core_recipes"}


def mods_of(key):
    """Conditions for the mods the ingredients come from (minecraft and this mod need none)."""
    mods = sorted({v.split(":")[0] for v in key.values()} - {"minecraft", MODID, "avaritia"})
    return [mod_loaded(mod) for mod in mods]


def write_recipe(name, recipe, conditions=()):
    conditions = list(conditions)
    write_json(os.path.join(RECIPES, name + ".json"),
               {"neoforge:conditions": conditions, **recipe} if conditions else recipe)


def shaped(pattern, key, output, count=1):
    """Crafting table recipe."""
    used = set("".join(pattern)) - {" "}
    assert used == set(key), (used, set(key))
    return {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {k: item(v) for k, v in key.items()},
        "pattern": pattern,
        "result": result(output, count),
    }


def extreme(pattern, key, output):
    """Re-Avaritia extreme crafting table recipe (9x9 grid = table tier 4)."""
    assert len(pattern) == 9 and all(len(row) == 9 for row in pattern), pattern
    used = set("".join(pattern)) - {" "}
    assert used == set(key), (used, set(key))
    return {
        "type": "avaritia:shaped_table",
        "key": {k: item(v) for k, v in key.items()},
        "pattern": pattern,
        "result": result(output),
        "tier": 4,
    }


# Avaritia item ids. Re-Avaritia calls the neutronium ingot / block "neutron_ingot" / "neutron"; a variant
# with "neutronium_ingot" / "neutronium_block" is written as well in case another port uses these names.
NEUTRONIUM_NAMES = [
    ("", {"ingot": "avaritia:neutron_ingot", "block": "avaritia:neutron"}),
    ("_neutronium", {"ingot": "avaritia:neutronium_ingot", "block": "avaritia:neutronium_block"}),
]


def generate_energy_storage_recipe():
    """Energy storage block: end game Mekanism and AE2 energy parts around an ultimate energy cube."""
    key = {"U": "mekanism:ultimate_energy_cube", "I": "mekanism:ultimate_induction_cell",
           "D": "ae2:dense_energy_cell", "A": "mekanism:pellet_antimatter"}
    write_recipe("energy_storage", shaped(["AIA", "DUD", "AIA"], key, ENERGY_STORAGE), mods_of(key))


def generate_filter_recipes():
    filter_id = f"{MODID}:resource_filter"
    write_recipe("resource_filter", shaped(
        ["IPI",
         "PHP",
         "IPI"],
        {"I": "minecraft:iron_ingot", "P": "minecraft:paper", "H": "minecraft:hopper"}, filter_id))
    # Crafting a filter by itself returns a blank one (the result carries no filter data).
    write_recipe("resource_filter_clear", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item(filter_id)],
        "result": result(filter_id),
    })


# Upgrade recipes: tier 1 around a basic control circuit, every next tier around the previous upgrade.
UPGRADE_KIND_ITEM = {"speed": "minecraft:redstone_block", "efficiency": "minecraft:lapis_block",
                     "energy": "minecraft:glowstone"}
UPGRADE_TIER_ITEM = {1: "minecraft:iron_ingot", 2: "mekanism:alloy_infused", 3: "mekanism:alloy_reinforced",
                     4: "mekanism:alloy_atomic"}


def generate_upgrade_recipes():
    for kind, kind_item in UPGRADE_KIND_ITEM.items():
        for tier in UPGRADE_TIERS:
            name = f"{kind}_upgrade_{tier}"
            centre = "mekanism:basic_control_circuit" if tier == 1 else f"{MODID}:{kind}_upgrade_{tier - 1}"
            key = {"M": UPGRADE_TIER_ITEM[tier], "K": kind_item, "C": centre}
            write_recipe(name, shaped(
                ["MKM",
                 "KCK",
                 "MKM"], key, f"{MODID}:{name}"), mods_of(key))


def generate_recipes():
    # ---- tier 1-5: crafting table, Mekanism + AE2 ----------------------------------------------
    key = {"F": "mekanism:steel_casing", "S": "mekanism:ingot_steel", "C": "mekanism:basic_control_circuit",
           "T": "mekanism:energy_tablet", "O": "mekanism:ingot_osmium"}
    write_recipe("generator_tier_1", shaped(
        ["SCS",
         "OFO",
         "STS"], key, gen(1)), mods_of(key))

    key = {"G": gen(1), "R": "mekanism:ingot_refined_glowstone", "A": "mekanism:alloy_infused",
           "C": "mekanism:advanced_control_circuit", "T": "mekanism:energy_tablet"}
    write_recipe("generator_tier_2", shaped(
        ["RCR",
         "AGA",
         "RTR"], key, gen(2)), mods_of(key))

    key = {"G": gen(2), "F": "ae2:fluix_block", "P": "ae2:engineering_processor",
           "O": "mekanism:ingot_refined_obsidian", "A": "mekanism:alloy_reinforced"}
    write_recipe("generator_tier_3", shaped(
        ["OPO",
         "AGA",
         "OFO"], key, gen(3)), mods_of(key))

    key = {"G": gen(3), "N": "minecraft:netherite_ingot", "K": "ae2:cell_component_64k",
           "A": "mekanism:alloy_atomic", "C": "mekanism:elite_control_circuit", "I": "ae2:interface"}
    write_recipe("generator_tier_4", shaped(
        ["NCN",
         "AGA",
         "KIK"], key, gen(4)), mods_of(key))

    key = {"G": gen(4), "C": "mekanism:ultimate_control_circuit", "P": "mekanism:pellet_polonium",
           "S": "ae2:singularity", "Q": "ae2:quantum_ring"}
    write_recipe("generator_tier_5", shaped(
        ["PCP",
         "QGQ",
         "PSP"], key, gen(5)), mods_of(key))

    # ---- tier 6-8 with Avaritia: extreme crafting table (9x9) ----------------------------------
    # Only loaded when Avaritia is installed (its recipe type is registered).
    key = {"G": gen(5), "P": "mekanism:pellet_plutonium", "C": "avaritia:crystal_matrix_ingot",
           "K": "ae2:cell_component_256k"}
    write_recipe("generator_tier_6", extreme(
        ["CCCPPPCCC",
         "CKPPCPPKC",
         "CPCCCCCPC",
         "PPCKPKCPP",
         "PCCPGPCCP",
         "PPCKPKCPP",
         "CPCCCCCPC",
         "CKPPCPPKC",
         "CCCPPPCCC"], key, gen(6)), [EXTREME_TABLE] + mods_of(key))

    for suffix, names in NEUTRONIUM_NAMES:
        key = {"G": gen(6), "A": "mekanism:pellet_antimatter", "N": names["ingot"], "S": "ae2:singularity"}
        write_recipe("generator_tier_7" + suffix, extreme(
            ["NNNNANNNN",
             "NSAAAAASN",
             "NANNNNNAN",
             "NANSASNAN",
             "AANAGANAA",
             "NANSASNAN",
             "NANNNNNAN",
             "NSAAAAASN",
             "NNNNANNNN"], key, gen(7)), [EXTREME_TABLE, exists(names["ingot"])] + mods_of(key))

        key = {"G": gen(7), "I": "avaritia:infinity_ingot", "C": "avaritia:infinity_catalyst",
               "N": names["block"], "A": "mekanism:pellet_antimatter"}
        write_recipe("generator_tier_8" + suffix, extreme(
            ["NNNIIINNN",
             "NAAICIAAN",
             "NAIIIIIAN",
             "IIICICIII",
             "ICIIGIICI",
             "IIICICIII",
             "NAIIIIIAN",
             "NAAICIAAN",
             "NNNIIINNN"], key, gen(8)), [EXTREME_TABLE, exists(names["block"])] + mods_of(key))

    # ---- tier 6-8 without Avaritia: crafting table, eight cores around the generator below ------
    # Nothing from Avaritia is needed. More expensive than the 9x9 recipes: the cores take the same
    # amount of the shared materials (or more), netherite / Mekanism multiblock parts instead of the
    # Avaritia materials, and every core contains the core of the tier below.
    key = {"P": "mekanism:pellet_plutonium", "D": "minecraft:diamond_block", "N": "minecraft:netherite_block",
           "K": "ae2:cell_component_256k", "S": "minecraft:nether_star"}
    write_recipe("crystal_core", shaped(
        ["PDP",
         "NSK",
         "PDP"], key, f"{MODID}:crystal_core"), [CORE_RECIPES] + mods_of(key))

    key = {"X": f"{MODID}:crystal_core", "A": "mekanism:pellet_antimatter", "C": "mekanism:sps_casing",
           "S": "ae2:singularity"}
    write_recipe("neutron_core", shaped(
        ["ACA",
         "SXS",
         "ACA"], key, f"{MODID}:neutron_core"), [CORE_RECIPES] + mods_of(key))

    key = {"X": f"{MODID}:neutron_core", "A": "mekanism:pellet_antimatter", "C": "mekanism:supercharged_coil",
           "Q": "ae2:quantum_link"}
    write_recipe("infinity_core", shaped(
        ["ACA",
         "QXQ",
         "ACA"], key, f"{MODID}:infinity_core"), [CORE_RECIPES] + mods_of(key))

    for tier, core in ((6, "crystal_core"), (7, "neutron_core"), (8, "infinity_core")):
        write_recipe(f"generator_tier_{tier}_cores", shaped(
            ["KKK",
             "KGK",
             "KKK"], {"G": gen(tier - 1), "K": f"{MODID}:{core}"}, gen(tier)), [CORE_RECIPES])


if __name__ == "__main__":
    generate_textures()
    generate_filter_item()
    generate_upgrade_items()
    generate_core_items()
    generate_energy_storage()
    generate_gui()
    generate_logo()
    generate_models()
    generate_loot_and_tags()
    shutil.rmtree(RECIPES, ignore_errors=True)   # recipes that are no longer generated must not stay behind
    generate_recipes()
    generate_filter_recipes()
    generate_upgrade_recipes()
    generate_energy_storage_recipe()
    print("resources written to", os.path.normpath(ROOT))
