// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.domain;


import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise.Visitor;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noises;

record DomainWarp(Noise x, Noise z, Noise mappedX, Noise mappedZ, Noise distance) implements Domain {
	
	public DomainWarp(Noise x, Noise z, Noise distance) {
		this(x, z, map(x), map(z), distance);
	}
	
	@Override
	public float getOffsetX(float x, float z, int seed) {
		return this.mappedX.compute(x, z, seed) * this.distance.compute(x, z, seed);
	}

	@Override
	public float getOffsetZ(float x, float z, int seed) {
		return this.mappedZ.compute(x, z, seed) * this.distance.compute(x, z, seed);
	}


	
    private static Noise map(Noise in) {
    	if (in.minValue() == -0.5F && in.maxValue() == 0.5F) {
    		return in;
    	}
    	return Noises.map(in, -0.5F, 0.5F);
    }
}
