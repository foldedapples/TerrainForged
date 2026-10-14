// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.settings;

import java.util.function.BiFunction;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noises;

public class ClimateSettings {
	
	public RangeValue temperature;
    public RangeValue moisture;
    public BiomeShape biomeShape;
    public BiomeNoise biomeEdgeShape;
    
    public ClimateSettings(RangeValue temperature, RangeValue moisture, BiomeShape biomeShape, BiomeNoise biomeEdgeShape) {
    	this.temperature = temperature;
    	this.moisture = moisture;
    	this.biomeShape = biomeShape;
    	this.biomeEdgeShape = biomeEdgeShape;
    }
    
    public ClimateSettings copy() {
    	return new ClimateSettings(this.temperature.copy(), this.moisture.copy(), this.biomeShape.copy(), this.biomeEdgeShape.copy());
    }
    
    public static class RangeValue {
    	
        public int seedOffset;
        public int scale;
        public int falloff;
        public float min;
        public float max;
        public float bias;
        
        public RangeValue(int seedOffset, int scale, int falloff, float min, float max, float bias) {
            this.seedOffset = seedOffset;
            this.min = min;
            this.max = max;
            this.bias = bias;
            this.scale = scale;
            this.falloff = falloff;
        }
        
        public float getMin() {
            return NoiseUtil.clamp(Math.min(this.min, this.max), 0.0F, 1.0F);
        }
        
        public float getMax() {
            return NoiseUtil.clamp(Math.max(this.min, this.max), this.getMin(), 1.0F);
        }
        
        public float getBias() {
            return NoiseUtil.clamp(this.bias, -1.0F, 1.0F);
        }
        
        public Noise apply(Noise module) {
            float min = this.getMin();
            float max = this.getMax();
            float bias = this.getBias() / 2.0F;
            module = Noises.add(module, bias);
            module = Noises.clamp(module, min, max);
            return module;
        }
        
        public RangeValue copy() {
        	return new RangeValue(this.seedOffset, this.scale, this.falloff, this.min, this.max, this.bias);
        }
    }
    
    public static class BiomeShape {
    	
        public int biomeSize;
        public int macroNoiseSize;
        public int biomeWarpScale;
        public int biomeWarpStrength;
        
        public BiomeShape(int biomeSize, int macroNoiseSize, int biomeWarpScale, int biomeWarpStrength) {
        	this.biomeSize = biomeSize;
        	this.macroNoiseSize = macroNoiseSize;
        	this.biomeWarpScale = biomeWarpScale;
        	this.biomeWarpStrength = biomeWarpStrength;
        }
        
        public BiomeShape copy() {
        	return new BiomeShape(this.biomeSize, this.macroNoiseSize, this.biomeWarpScale, this.biomeWarpStrength);
        }
    }
    
    public static class BiomeNoise {
    	
        public EdgeType type;
        public int scale;
        public int octaves;
        public float gain;
        public float lacunarity;
        public int strength;

        public BiomeNoise(EdgeType type, int scale, int octaves, float gain, float lacunarity, int strength) {
            this.type = type;
            this.scale = scale;
            this.octaves = octaves;
            this.gain = gain;
            this.lacunarity = lacunarity;
            this.strength = strength;
        }
        
        public Noise build(int seed) {
            return Noises.add(this.type.factory.apply(seed, this), Noises.constant(-0.5F));
        }
        
        public BiomeNoise copy() {
        	return new BiomeNoise(this.type, this.scale, this.octaves, this.gain, this.lacunarity, this.strength);
        }
        
        public enum EdgeType {
        	BILLOW("BILLOW", (seed, settings) -> Noises.billow(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	VORONOI("CELL", (seed, settings) -> Noises.worley(seed, settings.scale)),
        	VORONOI_EDGE("CELL_EDGE", (seed, settings) -> Noises.worleyEdge(seed, settings.scale)),
        	CONSTANT("CONST", (seed, settings) -> Noises.one()),
        	CUBIC("CUBIC", (seed, settings) -> Noises.cubic(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	PERLIN("PERLIN", (seed, settings) -> Noises.perlin(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	PERLIN2("PERLIN2", (seed, settings) -> Noises.perlin2(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	PERLIN_RIDGE("RIDGE", (seed, settings) -> Noises.perlinRidge(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	SIMPLEX("SIMPLEX", (seed, settings) -> Noises.simplex(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	SIMPLEX2("SIMPLEX2", (seed, settings) -> Noises.simplex2(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	
        	// calling simplex2 isn't a mistake, this is for backwards compatibility
        	SIMPLEX_RIDGE("SIMPLEX_RIDGE", (seed, settings) -> Noises.simplex2(seed, settings.scale, settings.octaves, settings.lacunarity, settings.gain)),
        	SIN("SIN", (seed, settings) -> Noises.sin(seed, 1.0F, Noises.zero())),
        	WHITE("RAND", (seed, settings) -> Noises.white(seed, 1));

        	
        	private String name;
        	private BiFunction<Integer, BiomeNoise, Noise> factory;
        	
        	private EdgeType(String name, BiFunction<Integer, BiomeNoise, Noise> factory) {
        		this.name = name;
        		this.factory = factory;
        	}

        	
        	public Noise build(int seed, BiomeNoise settings) {
        		return this.factory.apply(seed, settings);
        	}
        }
    }
}
