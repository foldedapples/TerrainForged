// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.function.CurveFunction;

public record Steps(Noise input, Noise steps, Noise slopeMin, Noise slopeMax, CurveFunction slopeCurve) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
		float noiseValue = this.input.compute(x, z, seed);
        float min = this.slopeMin.compute(x, z, seed);
        float max = this.slopeMax.compute(x, z, seed);
        float stepCount = this.steps.compute(x, z, seed);
        float range = max - min;
        if (range <= 0.0F) {
            return (int) (noiseValue * stepCount) / stepCount;
        }
        noiseValue = 1.0F - noiseValue;
        float value = (int) (noiseValue * stepCount) / stepCount;
        float delta = noiseValue - value;
        float alpha = NoiseUtil.map(delta * stepCount, min, max, range);
        return 1.0F - NoiseUtil.lerp(value, noiseValue, this.slopeCurve.apply(alpha));
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
