// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.biome;

public enum Humidity implements BiomeParameter {
    LEVEL_0(-1.0F, -0.35F),
    LEVEL_1(-0.35F, -0.1F),
    LEVEL_2(-0.1F, 0.1F),
    LEVEL_3(0.1F, 0.3F),
    LEVEL_4(0.3F, 1.0F);
	
	private float min;
    private float max;

    private Humidity(float min, float max) {
    	this.min = min;
    	this.max = max;
    }
    
    @Override
    public float min() {
    	return this.min;
    }
    
    @Override
    public float max() {
    	return this.max;
    }
}
