// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.biome;

public enum Erosion implements BiomeParameter {
    LEVEL_0(-1.0F, -0.78F),
    LEVEL_1(-0.78F, -0.375F),
    LEVEL_2(-0.375F, -0.2225F),
    LEVEL_3(-0.2225F, 0.05F),
    LEVEL_4(0.05F, 0.45F),
    LEVEL_5(0.45F, 0.55F),
    LEVEL_6(0.55F, 1.0F);
	
	private float min;
	private float max;
	
	private Erosion(float min, float max) {
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
