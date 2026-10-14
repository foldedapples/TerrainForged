// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.function.Interpolation;

record Blend(Noise alpha, Noise lower, Noise upper, float mid, float range, Interpolation interpolation) implements Noise {
	
	@Override
	public float minValue() {
		return Math.min(this.lower.minValue(), this.upper.minValue());
	}

	@Override
	public float maxValue() {
		return Math.max(this.lower.maxValue(), this.upper.maxValue());
	}
	
	@Override
	public float compute(float x, float z, int seed) {
        float mid = this.alpha.minValue() + (this.alpha.maxValue() - this.alpha.minValue()) * this.mid;
        float blendLower = Math.max(this.alpha.minValue(), mid - this.range / 2.0F);
        float blendUpper = Math.min(this.alpha.maxValue(), mid + this.range / 2.0F);
        float blendRange = blendUpper - blendLower;
		float alpha = this.alpha.compute(x, z, seed);
        if (alpha < blendLower) {
            return this.lower.compute(x, z, seed);
        }
        if (alpha > blendUpper) {
            return this.upper.compute(x, z, seed);
        }
        return NoiseUtil.lerp(this.lower.compute(x, z, seed), this.upper.compute(x, z, seed), this.interpolation.apply((alpha - blendLower) / blendRange));
	}


}
