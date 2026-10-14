// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.function;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;

public enum CellFunction {
    CELL_VALUE("CELL_VALUE") {
        
    	@Override
        public float apply(int seed, int xc, int yc, float distance, NoiseUtil.Vec2f vec2f, Noise lookup) {
            return NoiseUtil.valCoord2D(seed, xc, yc);
        }
    }, 
    NOISE_LOOKUP("NOISE_LOOKUP") {
    	
        @Override
        public float apply(int seed, int xc, int yc, float distance, NoiseUtil.Vec2f vec2f, Noise lookup) {
            return lookup.compute(xc + vec2f.x(), yc + vec2f.y(), seed);
        }
        
        @Override
        public float mapValue(float value, float min, float max, float range) {
            return value;
        }
    }, 
    DISTANCE("DISTANCE") {
        
    	@Override
        public float apply(int seed, int xc, int yc, float distance, NoiseUtil.Vec2f vec2f, Noise lookup) {
            return distance - 1.0F;
        }
        
        @Override
        public float mapValue(float value, float min, float max, float range) {
            return 0.0F;
        }
    };
	
    
	private String name;
	
	private CellFunction(String name) {
		this.name = name;
	}
	
	
    public abstract float apply(int seed, int xc, int yc, float distance, NoiseUtil.Vec2f vec2f, Noise lookup);
    
    public float mapValue(float value, float min, float max, float range) {
        return NoiseUtil.map(value, min, max, range);
    }
}
