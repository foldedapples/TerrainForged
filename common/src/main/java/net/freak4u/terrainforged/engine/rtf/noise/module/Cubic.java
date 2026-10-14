// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

public record Cubic(float frequency, int octaves, float lacunarity, float gain, float minValue, float maxValue) implements Noise {

	public Cubic(float frequency, int octaves, float lacunarity, float gain) {
		this(frequency, octaves, lacunarity, gain, calculateBound(-0.75F, octaves, gain), calculateBound(0.75F, octaves, gain));
	}
	
	@Override
	public float compute(float x, float z, int seed) {
		x *= this.frequency;
		z *= this.frequency;
		
		float sum = sample(x, z, seed);
		float amplifier = 1.0F;
		for(int i = 1; i < this.octaves; i++) {
    		x *= this.lacunarity;
    		z *= this.lacunarity;
    		amplifier *= this.gain;
    		
    		sum += sample(x, z, seed + i) * amplifier;
    	}
		return NoiseUtil.map(sum, this.minValue, this.maxValue, this.maxValue - this.minValue);
	}


	
    public static float sample(float x, float y, int seed) {
        int x2 = NoiseUtil.floor(x);
        int y2 = NoiseUtil.floor(y);
        int x3 = x2 - 1;
        int y3 = y2 - 1;
        int x4 = x2 + 1;
        int y4 = y2 + 1;
        int x5 = x2 + 2;
        int y5 = y2 + 2;
        float xs = x - x2;
        float ys = y - y2;
        return NoiseUtil.cubicLerp(NoiseUtil.cubicLerp(NoiseUtil.valCoord2D(seed, x3, y3), NoiseUtil.valCoord2D(seed, x2, y3), NoiseUtil.valCoord2D(seed, x4, y3), NoiseUtil.valCoord2D(seed, x5, y3), xs), NoiseUtil.cubicLerp(NoiseUtil.valCoord2D(seed, x3, y2), NoiseUtil.valCoord2D(seed, x2, y2), NoiseUtil.valCoord2D(seed, x4, y2), NoiseUtil.valCoord2D(seed, x5, y2), xs), NoiseUtil.cubicLerp(NoiseUtil.valCoord2D(seed, x3, y4), NoiseUtil.valCoord2D(seed, x2, y4), NoiseUtil.valCoord2D(seed, x4, y4), NoiseUtil.valCoord2D(seed, x5, y4), xs), NoiseUtil.cubicLerp(NoiseUtil.valCoord2D(seed, x3, y5), NoiseUtil.valCoord2D(seed, x2, y5), NoiseUtil.valCoord2D(seed, x4, y5), NoiseUtil.valCoord2D(seed, x5, y5), xs), ys) * 0.44444445f;
    }
    
    private static float calculateBound(float signal, int octaves, float gain) {
        float amp = 1.0F;
        float value = signal;
        for (int i = 1; i < octaves; ++i) {
            amp *= gain;
            value += signal * amp;
        }
        return value;
    }
}
