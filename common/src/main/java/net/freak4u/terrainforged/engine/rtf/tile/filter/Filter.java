// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.tile.filter;

import net.freak4u.terrainforged.engine.rtf.cell.Cell;

public interface Filter {
	void apply(Filterable map, int regionX, int regionZ, int iterationsPerChunk);

	default void iterate(Filterable map, Visitor visitor) {
		for (int dz = 0; dz < map.getBlockSize().total(); ++dz) {
			for (int dx = 0; dx < map.getBlockSize().total(); ++dx) {
				Cell cell = map.getCellRaw(dx, dz);
				visitor.visit(map, cell, dx, dz);
			}
		}
	}

	public interface Visitor {
		void visit(Filterable map, Cell cell, int dx, int dz);
	}
}
