// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;

import java.util.function.Function;


import net.freak4u.terrainforged.engine.rtf.noise.domain.Domain;
import net.freak4u.terrainforged.engine.rtf.noise.domain.Domains;
import net.freak4u.terrainforged.engine.rtf.noise.function.CellFunction;
import net.freak4u.terrainforged.engine.rtf.noise.function.CurveFunction;
import net.freak4u.terrainforged.engine.rtf.noise.function.DistanceFunction;
import net.freak4u.terrainforged.engine.rtf.noise.function.EdgeFunction;
import net.freak4u.terrainforged.engine.rtf.noise.function.Interpolation;

public class Noises {
    public static final float MAX_REASONABLE_NOISE_VALUE = 1000000.0F;

	public static Noise constant(float value) {
		return new Constant(value);
	}
	
	public static Noise negative(Noise input) {
		return Noises.mul(input, -1.0F);
	}

	public static Noise zero() {
		return constant(0.0F);
	}

	public static Noise one() {
		return constant(1.0F);
	}
	
	public static Noise sin(int seed, float frequency, Noise alpha) {
		return shiftSeed(new Sin(frequency, alpha), seed);
	}
	
	public static Noise white(int seed, int scale) {
		return shiftSeed(new White(1.0F / scale), seed);
	}
	
	public static Noise perlin(int seed, int scale, int octaves) {
		return perlin(seed, scale, octaves, 2.0F);
	}

	public static Noise perlin(int seed, int scale, int octaves, float lacunarity) {
		return perlin(seed, scale, octaves, lacunarity, 0.5F);
	}
	
	public static Noise perlin(int seed, int scale, int octaves, float lacunarity, float gain) {
		return new Perlin(seed, 1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3);
	}
	
	public static Noise perlin2(int seed, int scale, int octaves) {
		return perlin2(seed, scale, octaves, 2.0F);
	}

	public static Noise perlin2(int seed, int scale, int octaves, float lacunarity) {
		return perlin2(seed, scale, octaves, lacunarity, 0.5F);
	}
	
	public static Noise perlin2(int seed, int scale, int octaves, float lacunarity, float gain) {
		return new Perlin2(seed, 1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3);
	}
	
	public static Noise perlinRidge(int seed, int scale, int octaves) {
		return perlinRidge(seed, scale, octaves, 2.0F);
	}

	public static Noise perlinRidge(int seed, int scale, int octaves, float lacunarity) {
		return perlinRidge(seed, scale, octaves, lacunarity, 0.975F);
	}
	
	public static Noise perlinRidge(int seed, int scale, int octaves, float lacunarity, float gain) {
		return shiftSeed(new PerlinRidge(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
	}
	
	public static Noise simplex(int seed, int scale, int octaves) {
		return simplex(seed, scale, octaves, 2.0F);
	}

	public static Noise simplex(int seed, int scale, int octaves, float lacunarity) {
		return simplex(seed, scale, octaves, lacunarity, 0.5F);
	}
	
	public static Noise simplex(int seed, int scale, int octaves, float lacunarity, float gain) {
		return shiftSeed(new Simplex(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
	}
	
	public static Noise simplex2(int seed, int scale, int octaves) {
		return simplex2(seed, scale, octaves, 2.0F);
	}

	public static Noise simplex2(int seed, int scale, int octaves, float lacunarity) {
		return simplex2(seed, scale, octaves, lacunarity, 0.5F);
	}
	
	public static Noise simplex2(int seed, int scale, int octaves, float lacunarity, float gain) {
		return shiftSeed(new Simplex2(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
	}
	
	public static Noise simplexRidge(int seed, int scale, int octaves) {
		return simplexRidge(seed, scale, octaves, 2.0F);
	}

	public static Noise simplexRidge(int seed, int scale, int octaves, float lacunarity) {
		return simplexRidge(seed, scale, octaves, lacunarity, 0.975F);
	}
	
	public static Noise simplexRidge(int seed, int scale, int octaves, float lacunarity, float gain) {
		return shiftSeed(new SimplexRidge(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
	}
	
	public static Noise worley(int seed, int scale) {
		return worley(seed, scale, CellFunction.CELL_VALUE, DistanceFunction.EUCLIDEAN, zero());
	}
	
	public static Noise worley(int seed, int scale, CellFunction cellFunction, DistanceFunction distanceFunction, Noise lookup) {
		return shiftSeed(new Worley(1.0F / scale, 1.0F, cellFunction, distanceFunction, lookup), seed);
	}
	
	public static Noise worleyEdge(int seed, int scale) {
		return worleyEdge(seed, scale, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
	}
	
	public static Noise worleyEdge(int seed, int scale, EdgeFunction edgeFunction, DistanceFunction distanceFunction) {
		return shiftSeed(new WorleyEdge(1.0F / scale, 1.0F, edgeFunction, distanceFunction), seed);
	}
	
	public static Noise billow(int seed, int scale, int octaves) {
		return billow(seed, scale, octaves, 2.0F);
	}

	public static Noise billow(int seed, int scale, int octaves, float lacunarity) {
		return billow(seed, scale, octaves, lacunarity, 0.5F);
	}
	
	public static Noise billow(int seed, int scale, int octaves, float lacunarity, float gain) {
		return shiftSeed(new Billow(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
	}
	
	public static Noise cubic(int seed, int scale, int octaves) {
		return cubic(seed, scale, octaves, 2.0F);
	}

	public static Noise cubic(int seed, int scale, int octaves, float lacunarity) {
		return cubic(seed, scale, octaves, lacunarity, 0.5F);
	}
	
	public static Noise cubic(int seed, int scale, int octaves, float lacunarity, float gain) {
		return shiftSeed(new Cubic(1.0F / scale, octaves, lacunarity, gain), seed);
	}
	
	public static Noise line(float x1, float z1, float x2, float z2, Noise radiusSq, Noise fadeIn, Noise fadeOut, float feather) {
		return new Line(x1, z1, x2, z2, radiusSq, fadeIn, fadeOut, feather);
	}
	
	public static Noise shiftSeed(Noise input, int seed) {
		return new ShiftSeed(input, seed);
	}

	public static Noise frequency(Noise input, float freq) {
		return frequency(input, freq, freq);
	}
	
	public static Noise frequency(Noise input, float xFreq, float zFreq) {
		return frequency(input, constant(xFreq), constant(zFreq));
	}
	
	public static Noise frequency(Noise input, Noise xFreq, Noise zFreq) {
		return new Frequency(input, xFreq, zFreq);
	}
	
	public static Noise add(Noise input1, float input2) {
		return add(input1, constant(input2));
	}
	
	public static Noise add(Noise input1, Noise input2) {
		return new Add(input1, input2);
	}

	public static Noise mul(Noise input1, float input2) {
		return mul(input1, constant(input2));
	}
	
	public static Noise mul(Noise input1, Noise input2) {
		return new Multiply(input1, input2);
	}
	
	public static Noise pow(Noise input, float pow) {
		return new Power(input, pow);
	}
	
	public static Noise powCurve(Noise input, float pow) {
		return new PowerCurve(input, pow);
	}
	
	public static Noise curve(Noise input, CurveFunction curveFunction) {
		return new Curve(input, curveFunction);
	}
	
	public static Noise gradient(Noise input, float lower, float upper, float strength) {
		return gradient(input, constant(lower), constant(upper), constant(strength));
	}
	
	public static Noise gradient(Noise input, Noise lower, Noise upper, Noise strength) {
		return new Gradient(input, lower, upper, strength);
	}
	
	public static Noise terrace(Noise input, float lowerCurve, float upperCurve, float lowerHeight, float blendRange, int steps) {
        return terrace(input, constant(lowerCurve), constant(upperCurve), constant(lowerHeight), blendRange, steps);
    }

	public static Noise terrace(Noise input, Noise lowerCurve, Noise upperCurve, Noise lowerHeight, float blendRange, int steps) {
        return new Terrace(input, lowerCurve, upperCurve, lowerHeight, blendRange, steps);
    }

	public static Noise advancedTerrace(Noise input, float modulation, float mask, float slope, float blendMin, float blendMax, int steps, int octaves) {
        return advancedTerrace(input, constant(modulation), constant(mask), constant(slope), blendMin, blendMax, steps, octaves);
    }
	
    public static Noise advancedTerrace(Noise input, Noise modulation, Noise mask, Noise slope, float blendMin, float blendMax, int steps, int octaves) {
        return new AdvancedTerrace(input, modulation, mask, slope, blendMin, blendMax, steps, octaves);
    }
	
	public static Noise invert(Noise input) {
		return new Invert(input);
	}
	
	public static Noise blend(Noise selector, Noise input1, Noise input2, float mid, float range) {
		return new Blend(selector, input1, input2, mid, range, Interpolation.LINEAR);
	}
	
	public static Noise alpha(Noise input, float alpha) {
		return alpha(input, constant(alpha));
	}
	
	public static Noise alpha(Noise input, Noise alpha) {
		return new Alpha(input, alpha);
	}
	
	public static Noise boost(Noise input) {
		return boost(input, 1);
	}
	
	public static Noise boost(Noise input, int iterations) {
		return new Boost(input, iterations);
	}
	
	public static Noise steps(Noise input, int steps, float slopeMin, float slopeMax) {
		return steps(input, steps, slopeMin, slopeMax, Interpolation.LINEAR);
	}
	
	public static Noise steps(Noise input, int steps, float slopeMin, float slopeMax, CurveFunction slopeCurve) {
		return steps(input, constant(steps), constant(slopeMin), constant(slopeMax), slopeCurve);
	}

	public static Noise steps(Noise input, Noise steps, Noise slopeMin, Noise slopeMax) {
		return new Steps(input, steps, slopeMin, slopeMax, Interpolation.LINEAR);
	}
	
	public static Noise steps(Noise input, Noise steps, Noise slopeMin, Noise slopeMax, CurveFunction slopeCurve) {
		return new Steps(input, steps, slopeMin, slopeMax, slopeCurve);
	}
	
	public static Noise abs(Noise input) {
		return new Abs(input);
	}
	
	public static Noise map(Noise input, float from, float to) {
		return map(input, constant(from), constant(to));
	}
	
	public static Noise map(Noise input, Noise from, Noise to) {
        if (from.minValue() == from.maxValue() && from.minValue() == input.minValue() && to.minValue() == to.maxValue() && to.maxValue() == input.maxValue()) {
            return input;
        }
		return new Map(input, from, to);
	}

	public static Noise clamp(Noise input, float min, float max) {
		return clamp(input, constant(min), constant(max));
	}
	
	public static Noise clamp(Noise input, Noise min, Noise max) {
		return new Clamp(input, min, max);
	}
	
	public static Noise threshold(Noise input, float lower, float upper, float threshold) {
		return threshold(input, constant(lower), constant(upper), constant(threshold));
	}
	
	public static Noise threshold(Noise input, Noise lower, Noise upper, float threshold) {
		return threshold(input, lower, upper, constant(threshold));
	}
	
	public static Noise threshold(Noise input, Noise lower, Noise upper, Noise threshold) {
		return new Threshold(input, lower, upper, threshold);
	}
	
	public static Noise min(Noise input1, float input2) {
		return min(input1, constant(input2));
	}
	
	public static Noise min(Noise input1, Noise input2) {
		return new Min(input1, input2);
	}

	public static Noise max(Noise input1, float input2) {
		return max(input1, constant(input2));
	}
	
	public static Noise max(Noise input1, Noise input2) {
		return new Max(input1, input2);
	}
	
	public static Noise warpPerlin(Noise input, int seed, int scale, int octaves, float pow) {
		return warp(input, 
        	perlin(seed, scale, octaves), 
        	perlin(seed + 1, scale, octaves), 
        	pow
        );
	}
	
	public static Noise warpWhite(Noise input, int seed, int scale, float pow) {
		return warp(input, 
        	white(seed, scale), 
        	white(seed + 1, scale), 
        	pow
        );
	}
	
	public static Noise warp(Noise input, Noise warpX, Noise warpZ, float pow) {
		return warp(input, warpX, warpZ, constant(pow));
	}
	
	public static Noise warp(Noise input, Noise warpX, Noise warpZ, Noise pow) {
		return warp(input, Domains.domain(warpX, warpZ, pow));
	}
	
	public static Noise warp(Noise input, Domain domain) {
		return new Warp(input, domain);
	}

	@Deprecated
	public static Noise cache2d(Noise input) {
		return new Cache2d(input);
	}
	
	public static Noise erosion(Noise input, int seed, int octaves, float strength, float gridSize, float amplitude, float lacunarity, float distanceFallOff, Erosion.BlendMode blendMode) {
		return new Erosion(input, seed, octaves, strength, gridSize, amplitude, lacunarity, distanceFallOff, blendMode);
	}
}
