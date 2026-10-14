// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.continent;

public interface SimpleContinent extends Continent {
	float getEdgeValue(float x, float z);

	default float getDistanceToEdge(int cx, int cz, float dx, float dy) {
		return 1.0F;
	}

	default float getDistanceToOcean(int cx, int cz, float dx, float dy) {
		return 1.0F;
	}
}
