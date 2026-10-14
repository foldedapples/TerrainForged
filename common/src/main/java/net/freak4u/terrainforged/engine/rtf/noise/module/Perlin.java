// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.function.Interpolation;

record Perlin(@Deprecated int seed, float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation, float min, float max) implements Noise {

    private static final float[] SIGNALS = new float[] { 
    	1.0F, 0.9F, 0.83F, 0.75F, 0.64F, 0.62F, 0.61F 
    };

    public Perlin(int seed, float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation) {
    	this(seed, frequency, octaves, lacunarity, gain, interpolation, min(octaves, gain), max(octaves, gain));
    }
    
	@Override
	public float compute(float x, float z, int seed) {
		x *= this.frequency;
		z *= this.frequency;

		float sum = 0.0F;
		float amplitude = this.gain;
		for(int i = 0; i < this.octaves; ++i) {
			sum += sample(x, z, this.seed + i, this.interpolation) * amplitude;
			x *= this.lacunarity;
			z *= this.lacunarity;
			amplitude *= this.gain;
		}
        return NoiseUtil.map(sum, this.min, this.max, this.max - this.min);
	}

	@Override
	public float minValue() {
		return 0.0F;
	}

	@Override
	public float maxValue() {
		return 1.0F;
	}


	
    public static float sample(float x, float y, int seed, Interpolation interpolation) {
        int x2 = NoiseUtil.floor(x);
        int y2 = NoiseUtil.floor(y);
        int x3 = x2 + 1;
        int y3 = y2 + 1;
        float xs = interpolation.apply(x - x2);
        float ys = interpolation.apply(y - y2);
        float xd0 = x - x2;
        float yd0 = y - y2;
        float xd2 = xd0 - 1.0F;
        float yd2 = yd0 - 1.0F;
        float xf0 = NoiseUtil.lerp(NoiseUtil.gradCoord2D(seed, x2, y2, xd0, yd0), NoiseUtil.gradCoord2D(seed, x3, y2, xd2, yd0), xs);
        float xf2 = NoiseUtil.lerp(NoiseUtil.gradCoord2D(seed, x2, y3, xd0, yd2), NoiseUtil.gradCoord2D(seed, x3, y3, xd2, yd2), xs);
        return NoiseUtil.lerp(xf0, xf2, ys);
    }
    
    private static float min(int octaves, float gain) {
        return -max(octaves, gain);
    }
    
    private static float max(int octaves, float gain) {
        float signal = signal(octaves);
        float sum = 0.0F;
        float amp = gain;
        for (int i = 0; i < octaves; ++i) {
            sum += signal * amp;
            amp *= gain;
        }
        return sum;
    }
    
    private static float signal(int octaves) {
        int index = Math.min(octaves, SIGNALS.length - 1);
        return SIGNALS[index];
    }
}
