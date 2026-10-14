// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;	

public record LegacyTemperature(float frequency, int power) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
		z *= this.frequency;
		float sin = NoiseUtil.sin(z);
		sin = NoiseUtil.clamp(sin, -1.0F, 1.0F);
		float value = NoiseUtil.pow(sin, this.power);
		value = NoiseUtil.copySign(value, sin);
		return NoiseUtil.map(value, -1.0F, 1.0F, 2.0F);
	}

	@Override
	public float minValue() {
		return 0.0F;
	}

	@Override
	public float maxValue() {
		return 1.0F;
	}


}
