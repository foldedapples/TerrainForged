// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.domain;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise.Visitor;

record DirectionWarp(Noise direction, Noise strength) implements Domain {
	
	@Override
	public float getOffsetX(float x, float z, int seed) {
        float angle = this.direction.compute(x, z, seed) * 6.2831855F;
        return NoiseUtil.sin(angle) * this.strength.compute(x, z, seed);
	}

	@Override
	public float getOffsetZ(float x, float z, int seed) {
        float angle = this.direction.compute(x, z, seed) * 6.2831855F;
        return NoiseUtil.cos(angle) * this.strength.compute(x, z, seed);
	}


}
