// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.terrain;

public class CompositeTerrain extends Terrain {
    private boolean flat;
    private float erosion;
    
    CompositeTerrain(int id, Terrain a, Terrain b) {
        super(id, a.getName() + "-" + b.getName(), getDominant(a, b));
        this.flat = (a.isFlat() && b.isFlat());
        this.erosion = Math.min(a.erosionModifier(), b.erosionModifier());
    }
    
    @Override
    public float erosionModifier() {
        return this.erosion;
    }
    
    @Override
    public boolean isFlat() {
        return this.flat;
    }
    
    private static Terrain getDominant(Terrain a, Terrain b) {
        TerrainCategory typeA = a.getCategory();
        TerrainCategory typeB = a.getCategory();
        TerrainCategory dom = typeA.getDominant(typeB);
        if (dom == typeA) {
            return a;
        }
        return b;
    }
}
