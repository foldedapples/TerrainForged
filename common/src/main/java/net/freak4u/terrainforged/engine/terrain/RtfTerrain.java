package net.freak4u.terrainforged.engine.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.config.TerrainSettings.Key;
import net.freak4u.terrainforged.engine.rtf.GeneratorContext;
import net.freak4u.terrainforged.engine.rtf.biome.Continentalness;
import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.continent.MushroomIslandPopulator;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.ControlPoints;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Heightmap;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Levels;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.TerrainCategory;
import net.freak4u.terrainforged.engine.rtf.cell.terrain.TerrainType;
import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.function.DistanceFunction;
import net.freak4u.terrainforged.engine.rtf.settings.ClimateSettings;
import net.freak4u.terrainforged.engine.rtf.settings.ContinentType;
import net.freak4u.terrainforged.engine.rtf.settings.FilterSettings;
import net.freak4u.terrainforged.engine.rtf.settings.MiscellaneousSettings;
import net.freak4u.terrainforged.engine.rtf.settings.Preset;
import net.freak4u.terrainforged.engine.rtf.settings.RiverSettings;
import net.freak4u.terrainforged.engine.rtf.settings.SpawnType;
import net.freak4u.terrainforged.engine.rtf.settings.WorldSettings;
import net.freak4u.terrainforged.engine.rtf.tile.Tile;
import net.freak4u.terrainforged.engine.rtf.tile.generation.TileGenerator;

/**
 * The ReTerraForged 1.20.1 terrain pipeline ("RTF 0.0.6", ported in {@code engine.rtf}): continents, terrain regions
 * and their blending, mountain chains, volcanoes, the river/lake/wetland network carved into the heightmap, RTF's
 * climate, and droplet erosion + smoothing on 128x128-block tiles with a border. Finished tiles are reduced to
 * compact arrays and kept in a bounded LRU; each tile is generated once (other threads wait for it).
 */
public final class RtfTerrain {
    public static final int TILE_CHUNKS_LOG2 = 3; // 8 chunks = 128 blocks, RTF's default tile size
    public static final int TILE_BLOCKS = 16 << TILE_CHUNKS_LOG2;
    private static final int TILE_CAPACITY = 192;

    public static final byte FLAG_RIVER = 1, FLAG_LAKE = 2, FLAG_WETLAND = 4, FLAG_VOLCANO = 8, FLAG_VOLCANO_PIPE = 16,
        FLAG_BEACH = 32, FLAG_OCEAN = 64;
    /** Set only on preview-map samples: a TerrainForged mountain stream passes here (see StreamNetwork). */
    public static final byte FLAG_STREAM = (byte) 0x80;

    private final GeneratorContext ctx;
    private final Heightmap heightmap;
    private final TileGenerator tiles;
    private final Levels levels;
    private final ControlPoints cp;
    private final float terrainHeight;
    private final Preset preset;
    private final ThreadLocal<Cell> scratch = ThreadLocal.withInitial(Cell::new);
    private final LinkedHashMap<Long, CompletableFuture<TileData>> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<TileData>> eldest) {
            return size() > TILE_CAPACITY;
        }
    };
    private long tileHits, tileMisses;

    public RtfTerrain(long seed, TerrainSettings s) {
        this.preset = toPreset(s);
        this.ctx = new GeneratorContext(preset, (int) (seed ^ (seed >>> 32)), TILE_CHUNKS_LOG2, GeneratorContext.tileBorder(preset));
        this.heightmap = ctx.generator.getHeightmap();
        this.tiles = ctx.generator;
        this.levels = ctx.levels;
        this.cp = heightmap.controlPoints();
        this.terrainHeight = levels.worldHeight;
    }

    public Preset preset() {
        return preset;
    }

    public Levels levels() {
        return levels;
    }

    /** RTF's climate random seed (used by the decorators' white noise, as in RTF). */
    public int climateSeed() {
        return heightmap.climate().randomSeed();
    }

    public static Preset toPreset(TerrainSettings s) {
        int terrainHeight = (int) s.get(Key.TERRAIN_HEIGHT);
        WorldSettings world = new WorldSettings(
            new WorldSettings.Continent(ContinentType.values()[i(s, Key.CONTINENT_TYPE)], DistanceFunction.EUCLIDEAN, i(s, Key.CONTINENT_SCALE), f(s, Key.CONTINENT_JITTER),
                f(s, Key.CONTINENT_SKIPPING), f(s, Key.CONTINENT_SIZE_VARIANCE), i(s, Key.CONTINENT_OCTAVES), f(s, Key.CONTINENT_GAIN), f(s, Key.CONTINENT_LACUNARITY)),
            new WorldSettings.ControlPoints(MushroomIslandPopulator.DEFAULT_INLAND_POINT, MushroomIslandPopulator.DEFAULT_COAST_POINT,
                f(s, Key.DEEP_OCEAN), f(s, Key.SHALLOW_OCEAN), f(s, Key.BEACH), f(s, Key.COAST), f(s, Key.INLAND)),
            new WorldSettings.Properties(SpawnType.CONTINENT_CENTER, terrainHeight, 64, s.seaLevel(), -54));
        ClimateSettings climate = new ClimateSettings(
            new ClimateSettings.RangeValue(0, i(s, Key.TEMPERATURE_SCALE), i(s, Key.TEMPERATURE_FALLOFF), f(s, Key.TEMPERATURE_MIN), f(s, Key.TEMPERATURE_MAX), f(s, Key.TEMPERATURE_BIAS)),
            new ClimateSettings.RangeValue(0, i(s, Key.MOISTURE_SCALE), i(s, Key.MOISTURE_FALLOFF), f(s, Key.MOISTURE_MIN), f(s, Key.MOISTURE_MAX), f(s, Key.MOISTURE_BIAS)),
            new ClimateSettings.BiomeShape(i(s, Key.BIOME_SIZE), i(s, Key.MACRO_NOISE_SIZE), i(s, Key.BIOME_WARP_SCALE), i(s, Key.BIOME_WARP_STRENGTH)),
            new ClimateSettings.BiomeNoise(ClimateSettings.BiomeNoise.EdgeType.values()[i(s, Key.BIOME_EDGE_TYPE)], i(s, Key.BIOME_EDGE_SCALE), i(s, Key.BIOME_EDGE_OCTAVES),
                f(s, Key.BIOME_EDGE_GAIN), f(s, Key.BIOME_EDGE_LACUNARITY), i(s, Key.BIOME_EDGE_STRENGTH)));
        net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings terrain = new net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings(
            new net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings.General(0, i(s, Key.REGION_SIZE), f(s, Key.VERTICAL_SCALE), f(s, Key.TERRAIN_SCALE), s.get(Key.FANCY_MOUNTAINS) >= 0.5),
            t(s, Key.STEPPE_WEIGHT, Key.STEPPE_BASE_SCALE, Key.STEPPE_VERTICAL_SCALE, Key.STEPPE_HORIZONTAL_SCALE),
            t(s, Key.PLAINS_WEIGHT, Key.PLAINS_BASE_SCALE, Key.PLAINS_VERTICAL_SCALE, Key.PLAINS_HORIZONTAL_SCALE),
            t(s, Key.HILLS_WEIGHT, Key.HILLS_BASE_SCALE, Key.HILLS_VERTICAL_SCALE, Key.HILLS_HORIZONTAL_SCALE),
            t(s, Key.DALES_WEIGHT, Key.DALES_BASE_SCALE, Key.DALES_VERTICAL_SCALE, Key.DALES_HORIZONTAL_SCALE),
            t(s, Key.PLATEAU_WEIGHT, Key.PLATEAU_BASE_SCALE, Key.PLATEAU_VERTICAL_SCALE, Key.PLATEAU_HORIZONTAL_SCALE),
            t(s, Key.BADLANDS_WEIGHT, Key.BADLANDS_BASE_SCALE, Key.BADLANDS_VERTICAL_SCALE, Key.BADLANDS_HORIZONTAL_SCALE),
            t(s, Key.TORRIDONIAN_WEIGHT, Key.TORRIDONIAN_BASE_SCALE, Key.TORRIDONIAN_VERTICAL_SCALE, Key.TORRIDONIAN_HORIZONTAL_SCALE),
            t(s, Key.MOUNTAINS_WEIGHT, Key.MOUNTAINS_BASE_SCALE, Key.MOUNTAINS_VERTICAL_SCALE, Key.MOUNTAINS_HORIZONTAL_SCALE),
            t(s, Key.VOLCANO_WEIGHT, Key.VOLCANO_BASE_SCALE, Key.VOLCANO_VERTICAL_SCALE, Key.VOLCANO_HORIZONTAL_SCALE));
        RiverSettings rivers = new RiverSettings(0, i(s, Key.RIVER_COUNT),
            new RiverSettings.River(i(s, Key.MAIN_BED_DEPTH), i(s, Key.MAIN_MIN_BANK_HEIGHT), i(s, Key.MAIN_MAX_BANK_HEIGHT), i(s, Key.MAIN_BANK_WIDTH), i(s, Key.MAIN_BED_WIDTH), f(s, Key.MAIN_FADE)),
            new RiverSettings.River(i(s, Key.BRANCH_BED_DEPTH), i(s, Key.BRANCH_MIN_BANK_HEIGHT), i(s, Key.BRANCH_MAX_BANK_HEIGHT), i(s, Key.BRANCH_BANK_WIDTH), i(s, Key.BRANCH_BED_WIDTH), f(s, Key.BRANCH_FADE)),
            new RiverSettings.Lake(f(s, Key.LAKE_CHANCE), f(s, Key.LAKE_MIN_START_DISTANCE), f(s, Key.LAKE_MAX_START_DISTANCE), i(s, Key.LAKE_DEPTH),
                i(s, Key.LAKE_SIZE_MIN), i(s, Key.LAKE_SIZE_MAX), i(s, Key.LAKE_MIN_BANK_HEIGHT), i(s, Key.LAKE_MAX_BANK_HEIGHT)),
            new RiverSettings.Wetland(f(s, Key.WETLAND_CHANCE), i(s, Key.WETLAND_SIZE_MIN), i(s, Key.WETLAND_SIZE_MAX)));
        FilterSettings filters = new FilterSettings(
            new FilterSettings.Erosion(i(s, Key.EROSION_DROPLETS), i(s, Key.EROSION_LIFETIME), f(s, Key.EROSION_VOLUME), f(s, Key.EROSION_VELOCITY), f(s, Key.EROSION_RATE), f(s, Key.EROSION_DEPOSIT)),
            new FilterSettings.Smoothing(i(s, Key.SMOOTHING_ITERATIONS), f(s, Key.SMOOTHING_RADIUS), f(s, Key.SMOOTHING_RATE)));
        MiscellaneousSettings misc = new MiscellaneousSettings(s.get(Key.SMOOTH_SNOW) >= 0.5, 600, true, true, s.get(Key.EROSION_DECORATOR) >= 0.5, false,
            s.get(Key.NATURAL_SNOW) >= 0.5, true, true, true, true, 0.4F, 0.4F);
        return new Preset(world, climate, terrain, rivers, filters, misc);
    }

    /** The inverse of {@link #toPreset}: settings that reproduce an RTF preset (used for the built-in presets). */
    public static TerrainSettings fromPreset(Preset p) {
        java.util.Map<String, Double> m = new java.util.HashMap<>(TerrainSettings.defaults().toMap());
        var c = p.world().continent;
        put(m, Key.CONTINENT_SCALE, c.continentScale); put(m, Key.CONTINENT_TYPE, c.continentType.ordinal());
        put(m, Key.CONTINENT_JITTER, c.continentJitter); put(m, Key.CONTINENT_SKIPPING, c.continentSkipping);
        put(m, Key.CONTINENT_SIZE_VARIANCE, c.continentSizeVariance); put(m, Key.CONTINENT_OCTAVES, c.continentNoiseOctaves);
        put(m, Key.CONTINENT_GAIN, c.continentNoiseGain); put(m, Key.CONTINENT_LACUNARITY, c.continentNoiseLacunarity);
        var cp = p.world().controlPoints;
        put(m, Key.DEEP_OCEAN, cp.deepOcean); put(m, Key.SHALLOW_OCEAN, cp.shallowOcean); put(m, Key.BEACH, cp.beach);
        put(m, Key.COAST, cp.coast); put(m, Key.INLAND, cp.inland);
        put(m, Key.SEA_LEVEL, p.world().properties.seaLevel); put(m, Key.TERRAIN_HEIGHT, Math.min(p.world().properties.worldHeight, 256));
        var cl = p.climate();
        put(m, Key.TEMPERATURE_SCALE, cl.temperature.scale); put(m, Key.TEMPERATURE_FALLOFF, cl.temperature.falloff);
        put(m, Key.TEMPERATURE_MIN, cl.temperature.min); put(m, Key.TEMPERATURE_MAX, cl.temperature.max); put(m, Key.TEMPERATURE_BIAS, cl.temperature.bias);
        put(m, Key.MOISTURE_SCALE, cl.moisture.scale); put(m, Key.MOISTURE_FALLOFF, cl.moisture.falloff);
        put(m, Key.MOISTURE_MIN, cl.moisture.min); put(m, Key.MOISTURE_MAX, cl.moisture.max); put(m, Key.MOISTURE_BIAS, cl.moisture.bias);
        put(m, Key.BIOME_SIZE, cl.biomeShape.biomeSize); put(m, Key.MACRO_NOISE_SIZE, cl.biomeShape.macroNoiseSize);
        put(m, Key.BIOME_WARP_SCALE, cl.biomeShape.biomeWarpScale); put(m, Key.BIOME_WARP_STRENGTH, cl.biomeShape.biomeWarpStrength);
        put(m, Key.BIOME_EDGE_TYPE, cl.biomeEdgeShape.type.ordinal()); put(m, Key.BIOME_EDGE_SCALE, cl.biomeEdgeShape.scale);
        put(m, Key.BIOME_EDGE_OCTAVES, cl.biomeEdgeShape.octaves); put(m, Key.BIOME_EDGE_GAIN, cl.biomeEdgeShape.gain);
        put(m, Key.BIOME_EDGE_LACUNARITY, cl.biomeEdgeShape.lacunarity); put(m, Key.BIOME_EDGE_STRENGTH, cl.biomeEdgeShape.strength);
        var t = p.terrain();
        put(m, Key.REGION_SIZE, t.general.terrainRegionSize); put(m, Key.VERTICAL_SCALE, t.general.globalVerticalScale);
        put(m, Key.TERRAIN_SCALE, t.general.globalHorizontalScale); put(m, Key.FANCY_MOUNTAINS, t.general.fancyMountains ? 1 : 0);
        terrain(m, t.steppe, Key.STEPPE_WEIGHT, Key.STEPPE_BASE_SCALE, Key.STEPPE_VERTICAL_SCALE, Key.STEPPE_HORIZONTAL_SCALE);
        terrain(m, t.plains, Key.PLAINS_WEIGHT, Key.PLAINS_BASE_SCALE, Key.PLAINS_VERTICAL_SCALE, Key.PLAINS_HORIZONTAL_SCALE);
        terrain(m, t.hills, Key.HILLS_WEIGHT, Key.HILLS_BASE_SCALE, Key.HILLS_VERTICAL_SCALE, Key.HILLS_HORIZONTAL_SCALE);
        terrain(m, t.dales, Key.DALES_WEIGHT, Key.DALES_BASE_SCALE, Key.DALES_VERTICAL_SCALE, Key.DALES_HORIZONTAL_SCALE);
        terrain(m, t.plateau, Key.PLATEAU_WEIGHT, Key.PLATEAU_BASE_SCALE, Key.PLATEAU_VERTICAL_SCALE, Key.PLATEAU_HORIZONTAL_SCALE);
        terrain(m, t.badlands, Key.BADLANDS_WEIGHT, Key.BADLANDS_BASE_SCALE, Key.BADLANDS_VERTICAL_SCALE, Key.BADLANDS_HORIZONTAL_SCALE);
        terrain(m, t.torridonian, Key.TORRIDONIAN_WEIGHT, Key.TORRIDONIAN_BASE_SCALE, Key.TORRIDONIAN_VERTICAL_SCALE, Key.TORRIDONIAN_HORIZONTAL_SCALE);
        terrain(m, t.mountains, Key.MOUNTAINS_WEIGHT, Key.MOUNTAINS_BASE_SCALE, Key.MOUNTAINS_VERTICAL_SCALE, Key.MOUNTAINS_HORIZONTAL_SCALE);
        terrain(m, t.volcano, Key.VOLCANO_WEIGHT, Key.VOLCANO_BASE_SCALE, Key.VOLCANO_VERTICAL_SCALE, Key.VOLCANO_HORIZONTAL_SCALE);
        var r = p.rivers();
        put(m, Key.RIVER_COUNT, r.riverCount);
        put(m, Key.MAIN_BED_DEPTH, r.mainRivers.bedDepth); put(m, Key.MAIN_MIN_BANK_HEIGHT, r.mainRivers.minBankHeight);
        put(m, Key.MAIN_MAX_BANK_HEIGHT, r.mainRivers.maxBankHeight); put(m, Key.MAIN_BANK_WIDTH, r.mainRivers.bankWidth);
        put(m, Key.MAIN_BED_WIDTH, r.mainRivers.bedWidth); put(m, Key.MAIN_FADE, r.mainRivers.fade);
        put(m, Key.BRANCH_BED_DEPTH, r.branchRivers.bedDepth); put(m, Key.BRANCH_MIN_BANK_HEIGHT, r.branchRivers.minBankHeight);
        put(m, Key.BRANCH_MAX_BANK_HEIGHT, r.branchRivers.maxBankHeight); put(m, Key.BRANCH_BANK_WIDTH, r.branchRivers.bankWidth);
        put(m, Key.BRANCH_BED_WIDTH, r.branchRivers.bedWidth); put(m, Key.BRANCH_FADE, r.branchRivers.fade);
        put(m, Key.LAKE_CHANCE, r.lakes.chance); put(m, Key.LAKE_MIN_START_DISTANCE, r.lakes.minStartDistance);
        put(m, Key.LAKE_MAX_START_DISTANCE, r.lakes.maxStartDistance); put(m, Key.LAKE_DEPTH, r.lakes.depth);
        put(m, Key.LAKE_SIZE_MIN, r.lakes.sizeMin); put(m, Key.LAKE_SIZE_MAX, r.lakes.sizeMax);
        put(m, Key.LAKE_MIN_BANK_HEIGHT, r.lakes.minBankHeight); put(m, Key.LAKE_MAX_BANK_HEIGHT, r.lakes.maxBankHeight);
        put(m, Key.WETLAND_CHANCE, r.wetlands.chance); put(m, Key.WETLAND_SIZE_MIN, r.wetlands.sizeMin); put(m, Key.WETLAND_SIZE_MAX, r.wetlands.sizeMax);
        var f = p.filters();
        put(m, Key.EROSION_DROPLETS, f.erosion.dropletsPerChunk); put(m, Key.EROSION_LIFETIME, f.erosion.dropletLifetime);
        put(m, Key.EROSION_VOLUME, f.erosion.dropletVolume); put(m, Key.EROSION_VELOCITY, f.erosion.dropletVelocity);
        put(m, Key.EROSION_RATE, f.erosion.erosionRate); put(m, Key.EROSION_DEPOSIT, f.erosion.depositeRate);
        put(m, Key.SMOOTHING_ITERATIONS, f.smoothing.iterations); put(m, Key.SMOOTHING_RADIUS, f.smoothing.smoothingRadius);
        put(m, Key.SMOOTHING_RATE, f.smoothing.smoothingRate);
        var misc = p.miscellaneous();
        put(m, Key.EROSION_DECORATOR, misc.erosionDecorator ? 1 : 0); put(m, Key.NATURAL_SNOW, misc.naturalSnowDecorator ? 1 : 0);
        put(m, Key.SMOOTH_SNOW, misc.smoothLayerDecorator ? 1 : 0);
        return TerrainSettings.fromMap(m);
    }

    private static void put(java.util.Map<String, Double> m, Key k, double v) {
        // RTF stores floats: keep their shortest decimal form (0.7F -> 0.7, not 0.699999988) so presets round-trip
        m.put(k.id, Double.parseDouble(Float.toString((float) v)));
    }

    private static void terrain(java.util.Map<String, Double> m, net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings.Terrain t, Key w, Key b, Key v, Key h) {
        put(m, w, t.weight); put(m, b, t.baseScale); put(m, v, t.verticalScale); put(m, h, t.horizontalScale);
    }

    private static int i(TerrainSettings s, Key k) {
        return (int) Math.round(s.get(k));
    }

    private static float f(TerrainSettings s, Key k) {
        return (float) s.get(k);
    }

    private static net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings.Terrain t(TerrainSettings s, Key w, Key b, Key v, Key h) {
        return new net.freak4u.terrainforged.engine.rtf.settings.TerrainSettings.Terrain(f(s, w), f(s, b), f(s, v), f(s, h));
    }

    // ----------------------------------------------------------------------------------------- single cells (raw)

    /** RTF's uncached cell (terrain + rivers + climate, no erosion filters). Fills {@code out}. */
    public void sampleRaw(float x, float z, Sample out) {
        Cell c = scratch.get();
        c.reset();
        ctx.lookup.applyCell(c, x, z, true);
        fill(c, out);
    }

    /** Continent plate id only (cheap). */
    public long continentAt(float x, float z) {
        Cell c = scratch.get();
        c.reset();
        heightmap.continent().apply(c, x, z);
        return pack(c.continentX, c.continentZ);
    }

    /** RTF's own spawn search centre (centre of the continent nearest the origin). */
    public long spawnCenter() {
        return heightmap.continent().getNearestCenter(0.0F, 0.0F);
    }

    private volatile TileGenerator previewGenerator;

    /**
     * RTF's map-preview path: one 128x128 tile of cells spread {@code zoom} blocks apart around a centre (optional
     * filters - droplet erosion and smoothing - off, as in RTF's preset screen). {@code out} receives 128*128 samples.
     */
    public void preview(float centerX, float centerZ, float zoom, Sample[] out) {
        TileGenerator g = previewGenerator;
        if (g == null) previewGenerator = g = new TileGenerator(heightmap, new net.freak4u.terrainforged.engine.rtf.WorldFilters(ctx), TILE_CHUNKS_LOG2, 0);
        Tile tile = g.generateZoomed(centerX, centerZ, zoom, false);
        int[] k = {0};
        tile.iterate((cell, dx, dz) -> {
            Sample s = out[dz * TILE_BLOCKS + dx];
            if (s != null) fill(cell, s);
        });
    }

    /** Guards against NaN/inf from extreme user settings (e.g. degenerate noise ranges): fall back to ground level. */
    private float safeHeight(Cell c) {
        return Float.isFinite(c.height) ? c.height : levels.ground;
    }

    private void fill(Cell c, Sample out) {
        c.height = safeHeight(c);
        out.height = c.height * terrainHeight;
        out.gradient = c.gradient;
        out.sediment = c.sediment;
        out.erosion = c.erosion;
        out.weirdness = c.weirdness;
        out.continentalness = continentalness(c);
        out.temperature = c.temperature;
        out.moisture = c.moisture;
        out.riverMask = c.riverMask;
        out.flags = flags(c);
        out.continentId = pack(c.continentX, c.continentZ);
        out.heightRaw = c.height;
    }

    /** Vanilla continentalness for a cell, exactly as RTF's {@code CellSampler.Field.CONTINENT}. */
    public float continentalness(Cell cell) {
        float deepOcean = cp.deepOcean(), shallowOcean = cp.shallowOcean(), beach = cp.beach(), inland = cp.inland();
        if (cell.terrain.isDeepOcean()) {
            float alpha = NoiseUtil.clamp(cell.continentEdge, 0.0F, deepOcean);
            alpha = NoiseUtil.lerp(alpha, 0.0F, deepOcean, 0.0F, 1.0F);
            return NoiseUtil.lerp(Continentalness.DEEP_OCEAN.min() + 0.05F, Continentalness.DEEP_OCEAN.max(), alpha);
        }
        if (cell.terrain.isShallowOcean()) {
            float alpha = NoiseUtil.clamp(cell.continentEdge, deepOcean, shallowOcean);
            alpha = NoiseUtil.lerp(alpha, deepOcean, shallowOcean, 0.0F, 0.98F);
            return NoiseUtil.lerp(Continentalness.OCEAN.min(), Continentalness.OCEAN.max(), alpha);
        }
        if (cell.terrain.getDelegate() == TerrainCategory.BEACH && cell.height + cell.beachNoise < levels.water(5)) {
            float alpha = NoiseUtil.clamp(cell.continentEdge, shallowOcean, beach);
            alpha = NoiseUtil.lerp(alpha, shallowOcean, beach, 0.0F, 1.0F);
            return NoiseUtil.lerp(Continentalness.COAST.min(), Continentalness.COAST.max(), alpha);
        }
        float alpha = NoiseUtil.clamp(cell.continentEdge, beach, inland);
        alpha = NoiseUtil.lerp(alpha, beach, inland, 0.0F, 1.0F);
        return NoiseUtil.lerp(Continentalness.NEAR_INLAND.mid(), Continentalness.FAR_INLAND.max(), alpha);
    }

    private static byte flags(Cell c) {
        int f = 0;
        if (c.terrain.isRiver()) f |= FLAG_RIVER;
        if (c.terrain.isLake()) f |= FLAG_LAKE;
        if (c.terrain.isWetland()) f |= FLAG_WETLAND;
        if (c.terrain == TerrainType.VOLCANO) f |= FLAG_VOLCANO;
        if (c.terrain == TerrainType.VOLCANO_PIPE) f |= FLAG_VOLCANO_PIPE;
        if (c.terrain.getDelegate() == TerrainCategory.BEACH) f |= FLAG_BEACH;
        if (c.terrain.isDeepOcean() || c.terrain.isShallowOcean()) f |= FLAG_OCEAN;
        return (byte) f;
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** One column's worth of RTF output. */
    public static final class Sample {
        public double height;
        public float heightRaw, gradient, sediment, erosion, weirdness, continentalness, temperature, moisture, riverMask;
        public byte flags;
        public long continentId;
    }

    // ----------------------------------------------------------------------------------------- tiles (eroded)

    public TileData tile(int tileX, int tileZ) {
        long key = pack(tileX, tileZ);
        CompletableFuture<TileData> f;
        boolean owner = false;
        synchronized (cache) {
            f = cache.get(key);
            if (f == null) {
                f = new CompletableFuture<>();
                cache.put(key, f);
                owner = true;
                tileMisses++;
            } else {
                tileHits++;
            }
        }
        if (owner) {
            try {
                f.complete(build(tileX, tileZ));
            } catch (Throwable t) {
                synchronized (cache) {
                    cache.remove(key);
                }
                f.completeExceptionally(t);
            }
        }
        return f.join();
    }

    public TileData tileAt(int blockX, int blockZ) {
        return tile(Math.floorDiv(blockX, TILE_BLOCKS), Math.floorDiv(blockZ, TILE_BLOCKS));
    }

    private TileData build(int tileX, int tileZ) {
        Tile tile = tiles.generate(tileX, tileZ);
        TileData d = new TileData(tileX * TILE_BLOCKS, tileZ * TILE_BLOCKS);
        for (int z = 0; z < TILE_BLOCKS; z++) {
            for (int x = 0; x < TILE_BLOCKS; x++) {
                Cell c = tile.lookup(d.x0 + x, d.z0 + z);
                c.height = safeHeight(c);
                int i = z * TILE_BLOCKS + x;
                d.height[i] = c.height * terrainHeight;
                d.gradient[i] = c.gradient;
                d.sediment[i] = c.sediment;
                d.erosion[i] = c.erosion;
                d.weirdness[i] = c.weirdness;
                d.continentalness[i] = continentalness(c);
                d.temperature[i] = c.temperature;
                d.moisture[i] = c.moisture;
                d.riverMask[i] = c.riverMask;
                d.flags[i] = flags(c);
            }
        }
        return d;
    }

    public long[] stats() {
        synchronized (cache) {
            return new long[]{tileHits, tileMisses, cache.size()};
        }
    }

    /** Compact per-tile result (core 128x128 only, border discarded). */
    public static final class TileData {
        public final int x0, z0;
        private static final int N = TILE_BLOCKS * TILE_BLOCKS;
        public final float[] height = new float[N];
        public final float[] gradient = new float[N];
        public final float[] sediment = new float[N];
        public final float[] erosion = new float[N];
        public final float[] weirdness = new float[N];
        public final float[] continentalness = new float[N];
        public final float[] temperature = new float[N];
        public final float[] moisture = new float[N];
        public final float[] riverMask = new float[N];
        public final byte[] flags = new byte[N];

        TileData(int x0, int z0) {
            this.x0 = x0;
            this.z0 = z0;
        }

        public int index(int blockX, int blockZ) {
            return (blockZ - z0) * TILE_BLOCKS + (blockX - x0);
        }
    }
}
