// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

public record LegacyMoisture(Noise source, int power) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
        float noise = this.source.compute(x, z, seed);
        if (this.power < 2) {
            return noise;
        }
        noise = (noise - 0.5F) * 2.0F;
        float value = NoiseUtil.pow(noise, this.power);
        value = NoiseUtil.copySign(value, noise);
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
