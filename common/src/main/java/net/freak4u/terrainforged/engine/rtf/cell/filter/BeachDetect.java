// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.filter;

import net.freak4u.terrainforged.engine.rtf.GeneratorContext;
import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.ControlPoints;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Levels;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.TerrainType;
import net.freak4u.terrainforged.engine.rtf.tile.Size;
import net.freak4u.terrainforged.engine.rtf.tile.filter.Filter;
import net.freak4u.terrainforged.engine.rtf.tile.filter.Filterable;

public record BeachDetect(Levels levels, ControlPoints transition) implements Filter {
    
    @Override
    public void apply(Filterable map, int seedX, int seedZ, int iterations) {
        Size size = map.getBlockSize();
        int total = size.total();
        
        for(int x = 0; x < total; x++) {
        	for(int z = 0; z < total; z++) {
        		Cell cell = map.getCellRaw(x, z);
	        	if (cell.terrain.isCoast() && cell.continentEdge < this.transition.beach()) {
	                Cell n = map.getCellRaw(x, z - 8);
	                Cell s = map.getCellRaw(x, z + 8);
	                Cell e = map.getCellRaw(x + 8, z);
	                Cell w = map.getCellRaw(x - 8, z);
	                float gx = this.grad(e, w, cell);
	                float gz = this.grad(n, s, cell);
	                float d2 = gx * gx + gz * gz;
	                if (d2 < 0.275F) {
                		map.getCellRaw(x, z).terrain = TerrainType.BEACH;
	                }
	            }
	        }
        }
    }
    
    private float grad(Cell a, Cell b, Cell def) {
        int distance = 17;
        if (a.isAbsent()) {
            a = def;
            distance -= 8;
        }
        if (b.isAbsent()) {
            b = def;
            distance -= 8;
        }
        return (a.height - b.height) / distance;
    }
    
    public static BeachDetect make(GeneratorContext ctx) {
    	Levels levels = ctx.levels;
    	ControlPoints transition = ControlPoints.make(ctx.preset.world().controlPoints);
    	return new BeachDetect(levels, transition);
    }
}
