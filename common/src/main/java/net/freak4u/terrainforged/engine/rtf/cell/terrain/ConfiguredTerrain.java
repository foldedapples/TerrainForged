// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.terrain;

public class ConfiguredTerrain extends Terrain {
    private float erosionModifier;
    private boolean isMountain;
    private boolean overridesRiver;
    
    ConfiguredTerrain(int id, String name, TerrainCategory category, float erosionModifier) {
        this(id, name, category, erosionModifier, category.isMountain(), category.overridesRiver());
    }
    
    ConfiguredTerrain(int id, String name, TerrainCategory category, boolean overridesRiver) {
        this(id, name, category, category.erosionModifier(), category.isMountain(), overridesRiver);
    }
    
    ConfiguredTerrain(int id, String name, TerrainCategory category, boolean isMountain, boolean overridesRiver) {
        this(id, name, category, category.erosionModifier(), isMountain, overridesRiver);
    }
    
    ConfiguredTerrain(int id, String name, TerrainCategory category, float erosionModifier, boolean isMountain, boolean overridesRiver) {
        super(id, name, category);
        this.erosionModifier = erosionModifier;
        this.isMountain = isMountain;
        this.overridesRiver = overridesRiver;
    }
    
    @Override
    public boolean overridesRiver() {
        return this.overridesRiver;
    }
    
    @Override
    public boolean isMountain() {
        return this.isMountain;
    }
    
    @Override
    public float erosionModifier() {
        return this.erosionModifier;
    }
}
