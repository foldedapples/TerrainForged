package net.freak4u.terrainforged.engine.worldgen;

import net.freak4u.terrainforged.engine.rtf.noise.module.Noise;
import net.freak4u.terrainforged.engine.rtf.noise.module.Noises;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;

/**
 * The decision logic of TerraForged's erosion decorator and natural-snow decorator (ReTerraForged 1.20.1
 * {@code ErodeFeature} / {@code DecorateSnowFeature} / {@code ColumnDecorator}, MIT), kept loader-free so it can be
 * tested; the Minecraft features only apply the blocks. Values are RTF's default configuration
 * ({@code PresetConfiguredFeatures}: rock 30/140, dirt 40/95, steepness 0.65/0.475/0.4, modifiers 6/255, 3/255,
 * sediment 256 and 3/255, scree 0.55).
 */
public final class SurfaceDecorator {
    public enum Material { KEEP, ROCK, GROUND }

    // RTF ErodeFeature.Config defaults
    private static final int ROCK_VAR = 30, ROCK_MIN = 140, DIRT_VAR = 40, DIRT_MIN = 95;
    private static final float ROCK_STEEPNESS = 0.65F, DIRT_STEEPNESS = 0.475F, SCREE_STEEPNESS = 0.4F;
    private static final float HEIGHT_MODIFIER = 6F / 255F, SLOPE_MODIFIER = 3F / 255F, SEDIMENT_NOISE = 3F / 255F, SCREE_VALUE = 0.55F;

    private static final Noise VARIANCE = Noises.perlin(0, 100, 1);
    private final Noise rand;

    public SurfaceDecorator(TerrainEngine engine) {
        this.rand = Noises.white(engine.terrain().climateSeed(), 1);
    }

    private static float sampleNoise(float x, float z, int scale, int bias) {
        return VARIANCE.compute(x, z, 0) * (scale / 255F) + bias / 255F;
    }

    /** Whether the decorator touches this column at all (rivers, wetlands and volcano pipes keep their surface). */
    public boolean applies(ColumnSample c) {
        return !c.is(RtfTerrain.FLAG_RIVER) && !c.is(RtfTerrain.FLAG_WETLAND) && !c.is(RtfTerrain.FLAG_VOLCANO_PIPE);
    }

    /** RTF {@code ErodeFeature.getMaterial}: bare rock on steep or high ground, coarse ground on moderate slopes. */
    public Material material(ColumnSample c, int x, int z) {
        float height = c.heightRaw + rand.compute(x, z, 0) * HEIGHT_MODIFIER;
        float steepness = c.gradient + rand.compute(x, z, 1) * SLOPE_MODIFIER;
        if (steepness > ROCK_STEEPNESS || height > sampleNoise(x, z, ROCK_VAR, ROCK_MIN)) return Material.ROCK;
        if (steepness > DIRT_STEEPNESS && height > sampleNoise(x, z, DIRT_VAR, DIRT_MIN)) return Material.GROUND;
        return Material.KEEP;
    }

    /**
     * TerrainForged tuning for snow-covered ground (cold biomes): snow stays on everything except genuinely steep faces,
     * which become bare rock. RTF's height rule and coarse-dirt band are skipped there - in game they produced a noisy
     * white/brown/grey mosaic on snowy mountains.
     */
    public Material snowyMaterial(ColumnSample c, int x, int z) {
        float steepness = c.gradient + rand.compute(x, z, 1) * SLOPE_MODIFIER;
        return steepness > ROCK_STEEPNESS ? Material.ROCK : Material.KEEP;
    }

    /** RTF {@code ErodeFeature.placeScree}: loose gravel on steep, sediment-rich slopes. */
    public boolean scree(ColumnSample c, int x, int z) {
        float steepness = c.gradient + rand.compute(x, z, 1) * SLOPE_MODIFIER;
        if (steepness < SCREE_STEEPNESS) return false;
        float sediment = c.sediment * SEDIMENT_NOISE;
        float noise = rand.compute(x, z, 2) * SEDIMENT_NOISE;
        return sediment + noise > SCREE_VALUE;
    }

    /**
     * Snow slides off slopes too steep to hold it (RTF {@code DecorateSnowFeature.snowErosion}). TerrainForged keeps
     * only RTF's steepest rule (and the volcano bonus): RTF's extra height/moderate-slope rules stripped snow in
     * patches across whole mountainsides, which looked noisy in game.
     */
    public boolean snowErodes(ColumnSample c, int x, int z) {
        float var = -sampleNoise(x, z, 16, 0);
        float sNoise = rand.compute(x, z, 5) * SLOPE_MODIFIER;
        float vModifier = c.is(RtfTerrain.FLAG_VOLCANO) ? 0.15F : 0F;
        float steepness = c.gradient + var * 0.5F + sNoise + vModifier;
        return steepness > ROCK_STEEPNESS;
    }

    /**
     * RTF {@code DecorateSnowFeature.smoothSnow}: snow-layer count (1..8) from the fractional part of the exact
     * terrain height, so snow fills the gap between blocks and slopes look smooth; 0 = leave as is.
     */
    public int snowLayers(ColumnSample c) {
        double h = c.height;
        double depth = h - Math.floor(h); // RTF: getDepth(height) = fractional height
        if (depth <= 0) return 0;
        int level = (int) Math.round(depth * 8);
        return Math.max(0, Math.min(8, level));
    }
}
