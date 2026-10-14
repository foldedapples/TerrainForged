// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// TerrainForged changes: uncached single-cell path only (TerrainForged caches eroded tiles itself).
package net.freak4u.terrainforged.engine.rtf.cell.heightmap;

import net.freak4u.terrainforged.engine.rtf.GeneratorContext;
import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.TerrainType;

public class WorldLookup {
	private final float waterLevel;
	private final float beachLevel;
	private final Heightmap heightmap;

	public WorldLookup(GeneratorContext context) {
		this.heightmap = context.generator.getHeightmap();
		this.waterLevel = context.levels.water;
		this.beachLevel = context.levels.water(5);
	}

	public Heightmap getHeightmap() {
		return this.heightmap;
	}

	/** Raw (un-eroded) cell, as RTF computes it outside of tiles. */
	public void applyCell(Cell cell, float x, float z, boolean applyClimate) {
		this.heightmap.apply(cell, x, z, applyClimate);
		if (cell.terrain == TerrainType.COAST && cell.height > this.waterLevel && cell.height <= this.beachLevel) {
			cell.terrain = TerrainType.BEACH;
		}
	}
}
