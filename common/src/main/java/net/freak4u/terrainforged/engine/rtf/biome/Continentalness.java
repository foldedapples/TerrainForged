// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.biome;

public enum Continentalness implements BiomeParameter {
	MUSHROOM_FIELDS(-1.2F, -1.05F),
	DEEP_OCEAN(-1.05F, -0.455F),
	OCEAN(-0.455F, -0.19F),
	COAST(-0.19F, -0.11F),
	NEAR_INLAND(-0.11F, 0.03F),
	MID_INLAND(0.03F, 0.3F),
	FAR_INLAND(0.3F, 1.0F);

	private float min;
	private float max;
	
	private Continentalness(float min, float max) {
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
