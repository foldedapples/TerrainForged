// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.terrain.region;

import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.CellPopulator;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.RegionConfig;
import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil.Vec2f;
import net.freak4u.terrainforged.engine.rtf.noise.domain.Domain;
import net.freak4u.terrainforged.engine.rtf.noise.domain.Domains;
import net.freak4u.terrainforged.engine.rtf.noise.function.DistanceFunction;
import net.freak4u.terrainforged.engine.rtf.noise.function.EdgeFunction;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noises;	

public class RegionModule implements CellPopulator {
    private int seed;
    private float frequency;
    private float edgeMin;
    private float edgeMax;
    private float edgeRange;
    private Domain warp;
    
    public RegionModule(RegionConfig regionConfig) {
        this.seed = regionConfig.seed() + 7;
        this.edgeMin = 0.0F;
        this.edgeMax = 0.5F;
        this.edgeRange = this.edgeMax - this.edgeMin;
        this.frequency = 1.0F / regionConfig.scale();
        this.warp = Domains.domain(regionConfig.warpX(), regionConfig.warpZ(), Noises.constant(regionConfig.warpStrength()));
    }
    
    @Override
    public void apply(Cell cell, float x, float z) {
        float ox = this.warp.getOffsetX(x, z, 0);
        float oz = this.warp.getOffsetZ(x, z, 0);
        float px = x + ox;
        float py = z + oz;
        px *= this.frequency;
        py *= this.frequency;
        int cellX = 0;
        int cellY = 0;
        int xi = NoiseUtil.floor(px);
        int yi = NoiseUtil.floor(py);
        float edgeDistance = Float.MAX_VALUE;
        float edgeDistance2 = Float.MAX_VALUE;
        DistanceFunction dist = DistanceFunction.NATURAL;
        for (int dy = -1; dy <= 1; ++dy) {
            for (int dx = -1; dx <= 1; ++dx) {
                int cx = xi + dx;
                int cy = yi + dy;
                Vec2f vec = NoiseUtil.cell(this.seed, cx, cy);
                float vecX = cx + vec.x() * 0.7F;
                float vecY = cy + vec.y() * 0.7F;
                float distance = dist.apply(vecX - px, vecY - py);
                if (distance < edgeDistance) {
                    edgeDistance2 = edgeDistance;
                    edgeDistance = distance;
                    cellX = cx;
                    cellY = cy;
                }
                else if (distance < edgeDistance2) {
                    edgeDistance2 = distance;
                }
            }
        }
        cell.terrainRegionId = this.cellValue(this.seed, cellX, cellY);
        cell.terrainRegionEdge = this.edgeValue(edgeDistance, edgeDistance2);
    }
    
    private float cellValue(int seed, int cellX, int cellY) {
        float value = NoiseUtil.valCoord2D(seed, cellX, cellY);
        return NoiseUtil.map(value, -1.0F, 1.0F, 2.0F);
    }
    
    private float edgeValue(float distance, float distance2) {
        EdgeFunction edge = EdgeFunction.DISTANCE_2_DIV;
        float value = edge.apply(distance, distance2);
        float edgeValue = 1.0F - NoiseUtil.map(value, edge.min(), edge.max(), edge.range());
        edgeValue = NoiseUtil.pow(edgeValue, 1.5F);
        if (edgeValue < this.edgeMin) {
            return 0.0F;
        }
        if (edgeValue > this.edgeMax) {
            return 1.0F;
        }
        return (edgeValue - this.edgeMin) / this.edgeRange;
    }
}
