// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.terrain.populator;

import net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings;
import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.CellPopulator;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.Terrain;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;

public record TerrainPopulator(Terrain type, Noise base, Noise height, Noise erosion, Noise weirdness, float baseScale, float heightScale, float weight) implements CellPopulator, WeightedPopulator {
    
	public TerrainPopulator(Terrain type, Noise base, Noise height, Noise erosion, Noise weirdness, float weight) {
		this(type, base, height, erosion, weirdness, 1.0F, 1.0F, weight);
	}
	
    @Override
    public void apply(Cell cell, float x, float z) {
        float base = this.base.compute(x, z, 0) * this.baseScale;
        float height = this.height.compute(x, z, 0) * this.heightScale;

        cell.terrain = this.type;
        cell.height = Math.max(base + height, 0.0F);
        cell.erosion = this.erosion.compute(x, z, 0);
        cell.weirdness = this.weirdness.compute(x, z, 0);
    }
    
    public static TerrainPopulator make(Terrain type, Noise base, Noise height, Noise erosion, Noise weirdness, TerrainSettings.Terrain settings) {
    	return new TerrainPopulator(type, base, height, erosion, weirdness, settings.baseScale, settings.verticalScale, settings.weight);
    }
}
