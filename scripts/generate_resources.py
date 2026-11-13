#!/usr/bin/env python3
"""Generates the data-driven resources of the mod (pure Python 3, no third-party packages).

    python3 scripts/generate_resources.py

Writes into src/main/resources:
  * assets/pi_generator/textures/block/generator_tier_N.png   (16x16 animated strips, 16 frames each)
  * assets/pi_generator/textures/block/generator_tier_N.png.mcmeta
  * assets/pi_generator/{blockstates,models/block,models/item}/generator_tier_N.json
  * assets/pi_generator/textures/item/resource_filter.png, models/item/resource_filter.json
  * assets/pi_generator/{textures/item,models/item}/{speed,efficiency,energy}_upgrade_N.*
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


def generate_textures():
    tex_dir = os.path.join(ASSETS, "textures", "block")
    for tier in TIERS:
        path = os.path.join(tex_dir, f"generator_tier_{tier}.png")
        rows = []
        for frame in range(FRAMES):
            rows.extend(texture_frame(tier, frame))
        write_png(path, SIZE, SIZE * FRAMES, rows)
        write_json(path + ".mcmeta", {"animation": {"frametime": FRAMETIME[tier], "interpolate": True}})


# Resource filter item: a funnel with a mesh on top and a purple core.
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


def generate_filter_item():
    assert len(FILTER_ART) == SIZE and all(len(row) == SIZE for row in FILTER_ART), FILTER_ART
    write_png(os.path.join(ASSETS, "textures", "item", "resource_filter.png"), SIZE, SIZE,
              [[FILTER_PALETTE[c] for c in row] for row in FILTER_ART])
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


def upgrade_texture(kind, tier):
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
                if 4 <= x <= 11 and 3 <= y <= 8 and symbol[y - 3][x - 4] == "#":
                    px = mix(colour, WHITE, 0.35) if y <= 5 else colour
                if y in (10, 11) and x in (4, 6, 8, 10):                # tier pips
                    px = border if (x - 4) // 2 < tier else mul(UPGRADE_PANEL, 0.6)
            row.append(px + (255,) if px else (0, 0, 0, 0))
        rows.append(row)
    return rows


def generate_upgrade_items():
    for kind in UPGRADE_SYMBOLS:
        for tier in UPGRADE_TIERS:
            name = f"{kind}_upgrade_{tier}"
            write_png(os.path.join(ASSETS, "textures", "item", name + ".png"), SIZE, SIZE, upgrade_texture(kind, tier))
            write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})


# --------------------------------------------------------------------------- models / blockstates / loot

def generate_models():
    for tier in TIERS:
        name = f"generator_tier_{tier}"
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                   {"variants": {"": {"model": f"{MODID}:block/{name}"}}})
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MODID}:block/{name}"}})
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
    generate_models()
    generate_loot_and_tags()
    generate_recipes()
    generate_filter_recipes()
    generate_upgrade_recipes()
    print("resources written to", os.path.normpath(ROOT))
