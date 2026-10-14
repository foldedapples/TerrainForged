// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.util;

public class Seed {
    private int root;
    private int value;
    
    public Seed(int value) {
        this.root = value;
        this.value = value;
    }
    
    public int next() {
        return this.value++;
    }
    
    public int get() {
        return this.value;
    }
    
    public int root() {
        return this.root;
    }
    
    public Seed split() {
        return new Seed(this.root);
    }
    
    public Seed offset(int offset) {
        return new Seed(this.root + offset);
    }
}
