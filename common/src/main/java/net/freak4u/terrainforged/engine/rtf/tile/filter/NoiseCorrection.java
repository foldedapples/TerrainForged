// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.tile.filter;

import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Levels;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.TerrainCategory;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.TerrainType;
import net.freak4u.terrainforged.engine.rtf.tile.Size;

public record NoiseCorrection(Levels levels) implements Filter {
    
    @Override
    public void apply(Filterable map, int seedX, int seedZ, int iterations) {
        Size size = map.getBlockSize();
        int total = size.total();
        
        int quartTotal = ((total) >> 2);
        for(int quartX = 0; quartX < quartTotal; quartX++) {
        	for(int quartZ = 0; quartZ < quartTotal; quartZ++) {
        		int startX = ((quartX) << 2);
        		int startZ = ((quartZ) << 2);
        		int endX = ((quartX + 1) << 2);
        		int endZ = ((quartZ + 1) << 2);
        		
        		boolean isBeach = false;
        		
        		beachTest:
        		for(int x = startX; x < endX; x++) {
        			for(int z = startZ; z < endZ; z++) {
        				Cell cell = map.getCellRaw(x, z);
        				if(cell.terrain.getDelegate() == TerrainCategory.BEACH || ((cell.terrain.isShallowOcean() || cell.terrain.isDeepOcean()) && cell.height > this.levels.water)) {            				
        					isBeach = true;
        					break beachTest;
        				}
            		}
        		}
        		
        		if(isBeach) { 
            		for(int x = startX; x < endX; x++) {
            			for(int z = startZ; z < endZ; z++) {
            				map.getCellRaw(x, z).terrain = TerrainType.BEACH;
            			}
            		}
        		}
            }
        }
    }
}
