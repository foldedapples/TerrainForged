// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.util;

import java.util.Random;

public record Variance(float min, float range) {
    
    public float apply(float value) {
        return this.min + value * this.range;
    }
    
    public float apply(float value, float scaler) {
        return this.apply(value) * scaler;
    }
    
    public float next(FastRandom random) {
        return this.apply(random.nextFloat());
    }
    
    public float next(Random random) {
        return this.apply(random.nextFloat());
    }
    
    public float next(FastRandom random, float scalar) {
        return this.apply(random.nextFloat(), scalar);
    }
    
    public float next(Random random, float scalar) {
        return this.apply(random.nextFloat(), scalar);
    }
    
    public static Variance min(float min) {
        return new Variance(min, 1.0F - min);
    }
    
    public static Variance range(float range) {
        return new Variance(1.0F - range, range);
    }
    
    public static Variance of(float min, float range) {
        return new Variance(min, range);
    }
}
