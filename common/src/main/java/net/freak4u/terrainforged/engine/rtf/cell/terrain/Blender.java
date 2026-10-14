// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.terrain;

import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.CellPopulator;
import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.function.Interpolation;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;

public class Blender implements CellPopulator {
	private Noise control;
	private CellPopulator lower;
	private CellPopulator upper;
	private float blendLower;
	private float blendUpper;
	private float blendRange;
	private float midpoint;

	public Blender(Noise control, CellPopulator lower, CellPopulator upper, float min, float max, float split) {
		this.control = control;
		this.lower = lower;
		this.upper = upper;
		this.blendLower = min;
		this.blendUpper = max;
		this.blendRange = this.blendUpper - this.blendLower;
		this.midpoint = this.blendLower + this.blendRange * split;
	}

	@Override
	public void apply(Cell cell, float x, float y) {
		float select = this.control.compute(x, y, 0);
		if (select < this.blendLower) {
			this.lower.apply(cell, x, y);
			return;
		}
		if (select > this.blendUpper) {
			this.upper.apply(cell, x, y);
			return;
		}
		float alpha = Interpolation.LINEAR.apply((select - this.blendLower) / this.blendRange);
		this.lower.apply(cell, x, y);
		float lowerHeight = cell.height;
		float lowerErosion = cell.erosion;
		float lowerWeirdness = cell.weirdness;
		Terrain lowerType = cell.terrain;
		this.upper.apply(cell, x, y);
		float upperHeight = cell.height;
		float upperErosion = cell.erosion;
		float upperWeirdness = cell.weirdness;
		cell.height = NoiseUtil.lerp(lowerHeight, upperHeight, alpha);
		cell.erosion = NoiseUtil.lerp(lowerErosion, upperErosion, alpha);
		cell.weirdness = NoiseUtil.lerp(lowerWeirdness, upperWeirdness, alpha);
		if (select < this.midpoint) {
			cell.terrain = lowerType;
		}
	}
}
