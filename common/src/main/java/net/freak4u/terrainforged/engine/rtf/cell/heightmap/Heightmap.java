// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.heightmap;

import net.freak4u.terrainforged.engine.rtf.settings.Preset;
import net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings;
import net.freak4u.terrainforged.engine.rtf.settings.WorldSettings;
import net.freak4u.terrainforged.engine.rtf.GeneratorContext;
import net.freak4u.terrainforged.engine.rtf.biome.Erosion;
import net.freak4u.terrainforged.engine.rtf.biome.Weirdness;
import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.CellPopulator;
import net.freak4u.terrainforged.engine.rtf.cell.climate.Climate;
import net.freak4u.terrainforged.engine.rtf.cell.continent.Continent;
import net.freak4u.terrainforged.engine.rtf.cell.continent.ContinentLerper2;
import net.freak4u.terrainforged.engine.rtf.cell.continent.ContinentLerper3;
import net.freak4u.terrainforged.engine.rtf.cell.rivermap.Rivermap;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.Blender;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.Populators;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.TerrainType;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.populator.VolcanoPopulator;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.provider.TerrainProvider;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.region.RegionLerper;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.region.RegionModule;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.region.RegionSelector;
import net.freak4u.terrainforged.engine.rtf.noise.function.DistanceFunction;
import net.freak4u.terrainforged.engine.rtf.noise.function.EdgeFunction;
import net.freak4u.terrainforged.engine.rtf.noise.function.Interpolation;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noises;
import net.freak4u.terrainforged.engine.rtf.util.Seed;

public record Heightmap(CellPopulator terrain, CellPopulator region, Continent continent, Climate climate, Levels levels, ControlPoints controlPoints, float terrainFrequency, Noise beachNoise) {
	
	public void apply(Cell cell, float x, float z, boolean applyClimate) {
		this.applyTerrain(cell, x, z);
		this.applyRivers(cell, x, z, this.continent.getRivermap(cell));
		this.applyClimate(cell, x, z, applyClimate);
	}
	
	public void applyTerrain(Cell cell, float x, float z) {
        cell.terrain = TerrainType.FLATS;
        cell.beachNoise = this.beachNoise.compute(x, z, 0);
        
        this.continent.apply(cell, x, z);
        this.region.apply(cell, x, z);
        this.terrain.apply(cell, x * this.terrainFrequency, z * this.terrainFrequency);
	}
	
	public void applyRivers(Cell cell, float x, float z, Rivermap rivermap) {
        rivermap.apply(cell, x, z);
        VolcanoPopulator.modifyVolcanoType(cell, this.levels);
	}
	
	public void applyClimate(Cell cell, float x, float z, boolean applyClimate) {
		float riverValleyThreshold = 0.675F;
        if(cell.riverMask < riverValleyThreshold) {
        	cell.erosion = 0.445F;
        	cell.weirdness = 0.34F;
        }
        
        if(cell.terrain.isRiver()) {
            cell.erosion = -0.05F;
            cell.weirdness = -0.03F;
        }
        
        if(cell.terrain.isLake() && cell.height < this.levels.water) {
            cell.erosion = Erosion.LEVEL_4.mid();
            cell.weirdness = -0.03F;
        }
        if(cell.terrain.isWetland()) {
        	cell.erosion = Erosion.LEVEL_6.mid();
        	cell.weirdness = Weirdness.VALLEY.mid();
        }
        
        this.climate.apply(cell, x, z, applyClimate);

        if(cell.riverMask >= riverValleyThreshold && cell.macroBiomeId > 0.5F) { 
        	cell.weirdness = -cell.weirdness;
        }
	}
	
	public static Heightmap make(GeneratorContext context) {
    	
        Preset preset = context.preset;
        WorldSettings world = context.preset.world();
        ControlPoints controlPoints = ControlPoints.make(world.controlPoints);

        TerrainSettings terrainSettings = preset.terrain();
        TerrainSettings.General general = terrainSettings.general;
        float globalVerticalScale = general.globalVerticalScale;
        
        Seed regionWarp = context.seed.offset(8934);
        int regionWarpScale = 400;
        int regionWarpStrength = 200;
        
        RegionConfig regionConfig = new RegionConfig(
        	context.seed.root() + 789124, 
        	general.terrainRegionSize, 
        	Noises.simplex(regionWarp.next(), regionWarpScale, 1),
        	Noises.simplex(regionWarp.next(), regionWarpScale, 1), 
        	regionWarpStrength
        );
        Levels levels = context.levels;
        float terrainFrequency = 1.0F / terrainSettings.general.globalHorizontalScale;
        CellPopulator region = new RegionModule(regionConfig);

        Seed mountainSeed = context.seed.offset(general.terrainSeedOffset);
        Noise mountainShape = Noises.worleyEdge(mountainSeed.next(), 1000, EdgeFunction.DISTANCE_2_ADD, DistanceFunction.EUCLIDEAN);
        mountainShape = Noises.warpPerlin(mountainShape, mountainSeed.next(), 333, 2, 250.0F);
        mountainShape = Noises.curve(mountainShape, Interpolation.CURVE3);
        mountainShape = Noises.clamp(mountainShape, 0.0F, 0.9F);
        mountainShape = Noises.map(mountainShape, 0.0F, 1.0F);

        // RTF data pack: terrain/ground = constant(seaLevel / terrainScaler)
        Noise ground = Noises.constant(context.preset.world().properties.seaLevel / (float) context.preset.world().properties.terrainScaler());
        
        CellPopulator terrainRegions = new RegionSelector(TerrainProvider.generateTerrain(context.seed, terrainSettings, regionConfig, levels, ground));
        CellPopulator terrainRegionBorders = Populators.makeBorder(context.seed, ground, terrainSettings.plains, terrainSettings.steppe, globalVerticalScale);
        CellPopulator terrainBlend = new RegionLerper(terrainRegionBorders, terrainRegions);
        CellPopulator mountains = Populators.makeMountainChain(mountainSeed, ground, terrainSettings.mountains, globalVerticalScale, general.fancyMountains);
        Continent continent = world.continent.continentType.create(context.seed, context);
        Climate climate = Climate.make(continent, context);
        CellPopulator land = new Blender(mountainShape, terrainBlend, mountains, 0.3F, 0.8F, 0.575F);
        
        CellPopulator deepOcean = Populators.makeDeepOcean(context.seed.next(), levels.water);
        CellPopulator shallowOcean = Populators.makeShallowOcean(context.levels);
        CellPopulator coast = Populators.makeCoast(context.levels);
        
        CellPopulator oceans = new ContinentLerper3(deepOcean, shallowOcean, coast, controlPoints.deepOcean(), controlPoints.shallowOcean(), controlPoints.coast());
        CellPopulator terrain = new ContinentLerper2(oceans, land, controlPoints.shallowOcean(), controlPoints.inland());

        Noise beachNoise = Noises.perlin2(context.seed.next(), 20, 1);
        beachNoise = Noises.mul(beachNoise, context.levels.scale(5));
        return new Heightmap(terrain, region, continent, climate, levels, controlPoints, terrainFrequency, beachNoise);
	}
	
	private static CellPopulator makeIslandPopulator(GeneratorContext context) {
//		float islandCoastPoint = 0.01F;
//        float islandInlandPoint = 0.005F;
//        
//        CellPopulator land = (cell, x, z) -> {
//        	cell.height = context.levels.water(3);
//        };
//        CellPopulator oceans = new ContinentLerper3(deepOcean, shallowOcean, coast, controlPoints.deepOcean(), controlPoints.shallowOcean(), controlPoints.coast());
//        CellPopulator terrain = new ContinentLerper2(oceans, land, controlPoints.shallowOcean(), controlPoints.inland());
//        return terrain
		return null;
	}
}
