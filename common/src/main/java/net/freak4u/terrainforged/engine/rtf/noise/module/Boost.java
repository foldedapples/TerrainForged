// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

record Boost(Noise input, int iterations) implements Noise {
	
	public Boost {
		input = Noises.map(input, 0.0F, 1.0F);
		iterations = Math.max(1, iterations);
	}
	
	@Override
	public float compute(float x, float z, int seed) {
		float value = this.input.compute(x, z, seed);
        for (int i = 0; i < this.iterations; ++i) {
            value = NoiseUtil.pow(value, 1.0F - value);
        }
        return value;
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
