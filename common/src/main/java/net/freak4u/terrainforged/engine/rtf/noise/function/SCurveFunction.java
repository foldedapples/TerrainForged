// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.function;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

record SCurveFunction(float lower, float upper) implements CurveFunction {
	
	public SCurveFunction {
		upper = upper < 0.0F ? Math.max(-lower, upper) : upper;
	}

	@Override
	public float apply(float f) {
        return NoiseUtil.pow(f, this.lower + this.upper * f);
	}

}
