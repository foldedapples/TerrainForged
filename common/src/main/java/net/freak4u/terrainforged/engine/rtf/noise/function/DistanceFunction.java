// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.function;



public enum DistanceFunction {
    EUCLIDEAN("EUCLIDEAN") {
        @Override
        public float apply(float x, float y) {
            return x * x + y * y;
        }
    }, 
    MANHATTAN("MANHATTAN") {
        @Override
        public float apply(float x, float y) {
            return Math.abs(x) + Math.abs(y);
        }
    }, 
    NATURAL("NATURAL") {
        @Override
        public float apply(float x, float y) {
            return Math.abs(x) + Math.abs(y) + (x * x + y * y);
        }
    };
	
	
	private String name;
	
	private DistanceFunction(String name) {
		this.name = name;
	}
    
	
    public abstract float apply(float x, float z);
}
