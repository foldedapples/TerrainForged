// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.continent;

import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.CellPopulator;
import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

public record MushroomIslandPopulator(CellPopulator ocean, CellPopulator coast, CellPopulator land, float coastPoint, float inlandPoint) implements CellPopulator {
	public static final float DEFAULT_INLAND_POINT = 0.0F;
	public static final float DEFAULT_COAST_POINT = DEFAULT_INLAND_POINT + 0.1F;
	
	@Override
	public void apply(Cell cell, float x, float z) {
		if(cell.continentEdge < this.inlandPoint) {
			this.land.apply(cell, x, z);
			return;
		}
		
		if(cell.continentEdge < this.coastPoint) {
			this.coast.apply(cell, x, z);
			float coastHeight = cell.height;
			
			this.land.apply(cell, x, z);
			float landHeight = cell.height;
			
			cell.height = NoiseUtil.lerp(landHeight, coastHeight, NoiseUtil.map(cell.continentEdge, 0.0F, 1.0F, this.inlandPoint, this.coastPoint));
			return;
		}
		
		this.ocean.apply(cell, x, z);
	}
}
