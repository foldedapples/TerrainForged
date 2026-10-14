// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

record PowerCurve(Noise input, float power, float mid, float min, float max) implements Noise {

	public PowerCurve(Noise input, float power) {
		this(input, power, input.minValue() + (input.maxValue() - input.minValue()) / 2.0F);
	}
	
	private PowerCurve(Noise input, float power, float mid) {
		this(input, power, mid, mid - NoiseUtil.pow(mid - input.minValue(), power), mid + NoiseUtil.pow(input.maxValue() - mid, power));
	}

	@Override
	public float minValue() {
		return this.input.minValue();
	}

	@Override
	public float maxValue() {
		return this.input.maxValue();
	}
	
	@Override
	public float compute(float x, float z, int seed) {
		float input = this.input.compute(x, z, seed);
        if (input >= this.mid) {
            float part = input - this.mid;
            input = this.mid + NoiseUtil.pow(part, this.power);
        } else {
            float part = this.mid - input;
            input = this.mid - NoiseUtil.pow(part, this.power);
        }
        return NoiseUtil.map(input, this.min, this.max, this.max - this.min);
	}


}
