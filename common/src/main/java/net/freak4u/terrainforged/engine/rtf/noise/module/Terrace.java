// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.module;


import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

public record Terrace(Noise input, Noise ramp, Noise cliff, Noise rampHeight, float blendRange, Step[] steps) implements Noise {
	
	public Terrace(Noise input, Noise ramp, Noise cliff, Noise rampHeight, float blendRange, int steps) {
		this(input, ramp, cliff, rampHeight, blendRange, createSteps(input, blendRange, steps));
	}
	
	@Override
	public float compute(float x, float z, int seed) {
        float input = NoiseUtil.clamp(this.input.compute(x, z, seed), 0.0F, 0.999999F);
        int index = NoiseUtil.floor(input * this.steps.length);
        Step step = this.steps[index];
        if (index == this.steps.length - 1) {
            return step.value;
        }
        if (input < step.lowerBound) {
            return step.value;
        }
        if (input > step.upperBound) {
            Step next = this.steps[index + 1];
            return next.value;
        }
        float ramp = 1.0F - this.ramp.compute(x, z, seed) * 0.5F;
        float cliff = 1.0F - this.cliff.compute(x, z, seed) * 0.5F;
        float alpha = (input - step.lowerBound) / (step.upperBound - step.lowerBound);
        float value = step.value;
        if (alpha > ramp) {
            Step next2 = this.steps[index + 1];
            float rampSize = 1.0F - ramp;
            float rampAlpha = (alpha - ramp) / rampSize;
            float rampHeight = this.rampHeight.compute(x, z, seed);
            value += (next2.value - value) * rampAlpha * rampHeight;
        }
        if (alpha > cliff) {
            Step next2 = this.steps[index + 1];
            float cliffAlpha = (alpha - cliff) / (1.0F - cliff);
            value = NoiseUtil.lerp(value, next2.value, cliffAlpha);
        }
        return value;
	}

	@Override
	public float minValue() {
		return this.input.minValue();
	}

	@Override
	public float maxValue() {
		return this.input.maxValue();
	}


	
	private static Step[] createSteps(Noise input, float blendRange, int steps) {
        float min = input.minValue();
        float max = input.maxValue();
        float range = max - min;
        float spacing = range / (steps - 1);
        Step[] array = new Step[steps];
        for (int i = 0; i < steps; ++i) {
            float value = i * spacing;
            array[i] = Step.create(value, spacing, blendRange);
        }
        return array;
	}
	
	private record Step(float value, float lowerBound, float upperBound) {
	
		public static Step create(float value, float distance, float blendRange) {
			float blend = distance * blendRange;
			float bound = (distance - blend) / 2.0F;
            return new Step(value, value - bound, value + bound);
		}
	}
}
