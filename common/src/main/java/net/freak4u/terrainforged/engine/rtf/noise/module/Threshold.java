// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


public record Threshold(Noise input, Noise lower, Noise upper, Noise threshold) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
		if(this.input.compute(x, z, seed) > this.threshold.compute(x, z, seed)) {
			return this.upper.compute(x, z, seed);
		} else {
			return this.lower.compute(x, z, seed);
		}
	}

	@Override
	public float minValue() {
		return this.input.minValue();
	}

	@Override
	public float maxValue() {
		return this.input.maxValue();
	}


}
