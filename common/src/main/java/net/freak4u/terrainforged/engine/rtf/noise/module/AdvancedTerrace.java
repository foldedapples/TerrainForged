// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

record AdvancedTerrace(Noise source, Noise modulation, Noise mask, Noise slope, float blendMin, float blendMax, int steps, int octaves) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
		float value = this.source.compute(x, z, seed);
        if (value <= this.blendMin) {
            return value;
        }
        float mask = this.mask.compute(x, z, seed);
        if (mask == 0.0F) {
            return value;
        }
        float result = value;
        float slope = this.slope.compute(x, z, seed);
        float modulation = this.modulation.compute(x, z, seed);
        for (int i = 1; i <= this.octaves; ++i) {
            result = this.getStepped(result, this.steps * i);
            result = this.getSloped(value, result, slope);
        }
        result = this.getModulated(result, modulation);
        float alpha = this.getAlpha(value);
        if (mask != 1.0F) {
            alpha *= mask;
        }
        return NoiseUtil.lerp(value, result, alpha);
	}

	@Override
	public float minValue() {
		return this.source.minValue();
	}

	@Override
	public float maxValue() {
		return this.source.maxValue();
	}


	
    private float getModulated(float value, float modulation) {
        return (value + modulation) / (this.source.maxValue() + this.modulation.maxValue());
    }
    
    private float getStepped(float value, int steps) {
        value = (float) NoiseUtil.round(value * steps);
        return value / steps;
    }
    
    private float getSloped(float value, float stepped, float slope) {
        final float delta = value - stepped;
        final float amount = delta * slope;
        return stepped + amount;
    }
    
    private float getAlpha(float value) {
        if (value > this.blendMax) {
            return 1.0F;
        }
        return (value - this.blendMin) / (this.blendMax - this.blendMin);
    }
}
