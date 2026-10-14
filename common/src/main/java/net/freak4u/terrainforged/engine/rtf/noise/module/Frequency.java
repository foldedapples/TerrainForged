// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


record Frequency(Noise input, Noise xFreq, Noise zFreq) implements Noise {
	
	@Override
	public float compute(float x, float z, int seed) {
		float xFreq = this.xFreq.compute(x, z, seed);
		float zFreq = this.zFreq.compute(x, z, seed);
		return this.input.compute(x * xFreq, z * zFreq, seed);
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
