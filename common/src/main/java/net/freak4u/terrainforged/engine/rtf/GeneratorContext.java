// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// TerrainForged changes: no data-pack noise lookup and no tile cache (TerrainForged's RtfTerrain caches tiles).
package net.freak4u.terrainforged.engine.rtf;

import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Heightmap;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Levels;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.WorldLookup;
import net.freak4u.terrainforged.engine.rtf.settings.Preset;
import net.freak4u.terrainforged.engine.rtf.tile.generation.TileGenerator;
import net.freak4u.terrainforged.engine.rtf.util.Seed;

public class GeneratorContext {
    public Seed seed;
    public Levels levels;
    public Preset preset;
    public TileGenerator generator;
    public WorldLookup lookup;

    public GeneratorContext(Preset preset, int seed, int tileSize, int tileBorder) {
        this.preset = preset;
        this.seed = new Seed(seed);
        this.levels = new Levels(preset.world().properties.terrainScaler(), preset.world().properties.seaLevel);
        this.generator = new TileGenerator(Heightmap.make(this), new WorldFilters(this), tileSize, tileBorder);
        this.lookup = new WorldLookup(this);
    }

    /** RTF's default tile border for a preset (larger droplet lifetimes need a wider border). */
    public static int tileBorder(Preset preset) {
        return Math.min(2, Math.max(1, preset.filters().erosion.dropletLifetime / 16));
    }
}
