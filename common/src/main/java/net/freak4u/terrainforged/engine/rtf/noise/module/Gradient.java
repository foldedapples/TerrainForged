// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

record Gradient(Noise input, Noise lower, Noise upper, Noise strength) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
		float noiseValue = this.input.compute(x, z, seed);
        float upperBound = this.upper.compute(x, z, seed);
        if (noiseValue > upperBound) {
            return noiseValue;
        }
        float amount = this.strength.compute(x, z, seed);
        float lowerBound = this.lower.compute(x, z, seed);
        if (noiseValue < lowerBound) {
            return NoiseUtil.pow(noiseValue, 1.0F - amount);
        }
        float alpha = 1.0F - (noiseValue - lowerBound) / (upperBound - lowerBound);
        float power = 1.0F - amount * alpha;
        return NoiseUtil.pow(noiseValue, power);
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
