// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.domain;


import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noises;

public class Domains {

	public static Domain domainPerlin(int seed, int scale, int octaves, float strength) {
		return domain(
			Noises.perlin(seed, scale, octaves), 
			Noises.perlin(seed + 1, scale, octaves), 
			Noises.constant(strength)
		);
	}
	
	public static Domain domainSimplex(int seed, int scale, int octaves, float strength) {
		return domain(
			Noises.simplex(seed, scale, octaves), 
			Noises.simplex(seed + 1, scale, octaves), 
			Noises.constant(strength)
		);
	}
	
	public static Domain domain(Noise x, Noise z, Noise distance) {
		return new DomainWarp(x, z, distance);
	}
	
	public static Domain direction(Noise direction, Noise distance) {
		return new DirectionWarp(direction, distance);
	}
	
	public static Domain compound(Domain input1, Domain input2) {
		return new CompoundWarp(input1, input2);
	}
	
	public static Domain add(Domain input1, Domain input2) {
		return new AddWarp(input1, input2);
	}
	
	public static Domain direct() {
		return new DirectWarp();
	}
}
