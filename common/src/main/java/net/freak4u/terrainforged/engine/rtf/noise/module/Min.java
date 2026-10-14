// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


public record Min(Noise input1, Noise input2) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
		return Math.min(this.input1.compute(x, z, seed), this.input2.compute(x, z, seed));
	}

	@Override
	public float minValue() {
		return Math.min(this.input1.minValue(), this.input2.minValue());
	}

	@Override
	public float maxValue() {
		return Math.min(this.input1.maxValue(), this.input2.maxValue());
	}


}
