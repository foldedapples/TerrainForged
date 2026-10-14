// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

public record White(float frequency) implements Noise {

	@Override
	public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;
        float value = sample(x, z, seed);
        return Math.abs(value);
	}

	@Override
	public float minValue() {
		return 0.0F;
	}

	@Override
	public float maxValue() {
		return 1.0F;
	}


	
	public static float sample(float x, float z, int seed) {
        int xi = NoiseUtil.round(x);
        int zi = NoiseUtil.round(z);
        return NoiseUtil.valCoord2D(seed, xi, zi);
    }
	
	public static float sample(float x, float z, int seed, int offset) {
		return sample(x, z, NoiseUtil.hash(seed, offset));
	}
}
