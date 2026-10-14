#!/usr/bin/env python3
"""
Generates TerrainForged's data-pack JSON (world presets with inline noise settings, dimension type, feature and
noise files) from the vanilla 1.21.11 overworld noise settings.

Usage:  python3 tools/generate_presets.py <path-to-vanilla-data/minecraft> [output-resources-dir]

The vanilla data folder can be extracted from the 1.21.11 client/server jar (data/minecraft/...). The generated
files are committed under common-mc/src/main/resources, so this script only needs re-running when the vanilla
router changes (e.g. porting to a new Minecraft version) or the preset layout changes.

Router mapping (see EngineDensityFunction):
  final_density                  vanilla cave/aquifer/noodle composition, with every reference to
                                 minecraft:overworld/sloped_cheese replaced by terrainforged:engine{field: terrain}
                                 and the top/bottom slides moved to the dimension's real limits
  preliminary_surface_level      terrainforged:engine{field: height}
  depth                          terrainforged:engine{field: depth}
  continents/erosion/ridges      terrainforged:engine{continentalness|erosion|weirdness}
  temperature/vegetation         terrainforged:engine{temperature|humidity}
  everything else (aquifer, ore veins, surface rule, spawn target) unchanged from vanilla
"""
import copy
import json
import os
import sys

PRESETS = [
    # name, min_y, height, dimension type, config profile marker
    ("terrainforged", -64, 384, "minecraft:overworld", 1),
    ("terrainforged_tall", -64, 2048, "terrainforged:overworld_tall", 2),
]


def engine(field, min_y, max_y, profile):
    settings = {"from_config": float(profile)}
    return {
        "type": "terrainforged:engine",
        "field": field,
        "seed_noise": "terrainforged:seed_probe",
        "settings": settings,
        "min_y": min_y,
        "max_y": max_y,
    }


def replace(node, target, repl):
    if isinstance(node, str):
        return copy.deepcopy(repl) if node == target else node
    if isinstance(node, list):
        return [replace(n, target, repl) for n in node]
    if isinstance(node, dict):
        return {k: replace(v, target, repl) for k, v in node.items()}
    return node


def move_slides(node, min_y, max_y):
    """Vanilla slides: bottom -64..-40, top 240..256 for a 320 top. Shift them to this dimension's limits."""
    if isinstance(node, list):
        return [move_slides(n, min_y, max_y) for n in node]
    if not isinstance(node, dict):
        return node
    out = {k: move_slides(v, min_y, max_y) for k, v in node.items()}
    if out.get("type") == "minecraft:y_clamped_gradient":
        if out["from_y"] == -64 and out["to_y"] == -40:
            out["from_y"], out["to_y"] = min_y, min_y + 24
        elif out["from_y"] == 240 and out["to_y"] == 256:
            out["from_y"], out["to_y"] = max_y - 80, max_y - 64
    return out


def main():
    vanilla = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "..", "common-mc", "src", "main", "resources")
    base = json.load(open(os.path.join(vanilla, "worldgen", "noise_settings", "overworld.json")))
    normal = json.load(open(os.path.join(vanilla, "worldgen", "world_preset", "normal.json")))
    dim_type = json.load(open(os.path.join(vanilla, "dimension_type", "overworld.json")))

    def write(rel, obj):
        p = os.path.join(out_dir, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w") as f:
            json.dump(obj, f, indent=2)
            f.write("\n")

    for name, min_y, height, dtype, profile in PRESETS:
        max_y = min_y + height
        ns = copy.deepcopy(base)
        ns["noise"]["min_y"] = min_y
        ns["noise"]["height"] = height
        r = ns["noise_router"]
        r["final_density"] = move_slides(replace(r["final_density"], "minecraft:overworld/sloped_cheese",
                                                 engine("terrain", min_y, max_y, profile)), min_y, max_y)
        r["preliminary_surface_level"] = engine("height", min_y, max_y, profile)
        r["depth"] = engine("depth", min_y, max_y, profile)
        r["continents"] = engine("continentalness", min_y, max_y, profile)
        r["erosion"] = engine("erosion", min_y, max_y, profile)
        r["ridges"] = engine("weirdness", min_y, max_y, profile)
        r["temperature"] = engine("temperature", min_y, max_y, profile)
        r["vegetation"] = engine("humidity", min_y, max_y, profile)
        preset = copy.deepcopy(normal)
        ow = preset["dimensions"]["minecraft:overworld"]
        ow["type"] = dtype
        ow["generator"]["settings"] = ns  # inline: saved into level.dat with the world
        write(f"data/terrainforged/worldgen/world_preset/{name}.json", preset)

    tall = copy.deepcopy(dim_type)
    tall["height"] = 2048
    tall["logical_height"] = 2048
    tall["min_y"] = -64
    write("data/terrainforged/dimension_type/overworld_tall.json", tall)

    write("data/terrainforged/worldgen/noise/seed_probe.json", {"firstOctave": -7, "amplitudes": [1.0, 1.0]})
    for feature in ("erode", "decorate_snow"):
        write("data/terrainforged/worldgen/configured_feature/%s.json" % feature,
              {"type": "terrainforged:%s" % feature, "config": {}})
        write("data/terrainforged/worldgen/placed_feature/%s.json" % feature,
              {"feature": "terrainforged:%s" % feature, "placement": []})
    write("data/minecraft/tags/worldgen/world_preset/normal.json",
          {"replace": False, "values": ["terrainforged:terrainforged", "terrainforged:terrainforged_tall"]})
    # the lang file also holds hand-written UI strings: only the preset names are (re)generated here
    lang_path = os.path.join(out_dir, "assets/terrainforged/lang/en_us.json")
    lang = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
    lang.update({
        "generator.terrainforged.terrainforged": "TerrainForged",
        "generator.terrainforged.terrainforged_tall": "TerrainForged (Tall 2048)",
    })
    write("assets/terrainforged/lang/en_us.json", lang)
    print("generated into", os.path.abspath(out_dir))


if __name__ == "__main__":
    main()
