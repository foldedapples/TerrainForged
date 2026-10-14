// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.tile.filter;

import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Levels;
import net.freak4u.terrainforged.engine.rtf.tile.filter.Filter.Visitor;

public record Steepness(int radius, float scaler, float waterLevel) implements Filter, Visitor {

	@Override
	public void apply(Filterable cellMap, int seedX, int seedZ, int iterations) {
		this.iterate(cellMap, this);
	}

	@Override
	public void visit(Filterable cellMap, Cell cell, int cx, int cz) {
		float totalHeightDif = 0.0F;
		for (int dz = -1; dz <= 2; ++dz) {
			for (int dx = -1; dx <= 2; ++dx) {
				if (dx != 0 || dz != 0) {
					int x = cx + dx * this.radius;
					int z = cz + dz * this.radius;
					Cell neighbour = cellMap.getCellRaw(x, z);
					if (!neighbour.isAbsent()) {
						float height = Math.max(neighbour.height, this.waterLevel);
						totalHeightDif += Math.abs(cell.height - height) / this.radius;
					}
				}
			}
		}
		cell.gradient = Math.min(1.0F, totalHeightDif * this.scaler);
	}
	
	public static Steepness make(int radius, float scaler, Levels levels) {
		return new Steepness(radius, scaler, levels.water);
	}
}
