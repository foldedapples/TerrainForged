package net.freak4u.terrainforged.engine.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every world-affecting TerrainForged setting. Immutable, validated (clamped) on construction, and
 * convertible to/from a flat name->number map so it can be stored inside the world's chunk generator
 * (level.dat). Unknown keys are ignored and missing keys take defaults; a world never re-reads the global config
 * after creation.
 *
 * <p>Since 0.3.0 the keys mirror ReTerraForged 1.20.1's preset (world, climate, terrain, rivers, filters,
 * decoration) with RTF's "Default" preset values and the ranges of RTF's preset editor.
 */
public final class TerrainSettings {

    public enum Key {
        // --- world: continents ---
        CONTINENT_SCALE("world.continent_scale", 3000, 100, 10000),
        CONTINENT_TYPE("world.continent_type", 0, 0, 4),
        CONTINENT_JITTER("world.continent_jitter", 0.8, 0.5, 1.0),
        CONTINENT_SKIPPING("world.continent_skipping", 0.25, 0.0, 1.0),
        CONTINENT_SIZE_VARIANCE("world.continent_size_variance", 0.25, 0.0, 0.75),
        CONTINENT_OCTAVES("world.continent_noise_octaves", 5, 1, 5),
        CONTINENT_GAIN("world.continent_noise_gain", 0.26, 0.0, 0.5),
        CONTINENT_LACUNARITY("world.continent_noise_lacunarity", 4.33, 1.0, 10.0),
        // --- world: control points (fractions of the continent field; kept in ascending order) ---
        DEEP_OCEAN("world.control.deep_ocean", 0.1, 0.0, 1.0),
        SHALLOW_OCEAN("world.control.shallow_ocean", 0.25, 0.0, 1.0),
        BEACH("world.control.beach", 0.327, 0.0, 1.0),
        COAST("world.control.coast", 0.448, 0.0, 1.0),
        INLAND("world.control.inland", 0.502, 0.0, 1.0),
        // --- world: properties ---
        SEA_LEVEL("world.sea_level", 63, 32, 400),
        TERRAIN_HEIGHT("world.terrain_height", 256, 128, 2000),
        // --- climate ---
        TEMPERATURE_SCALE("climate.temperature.scale", 7, 1, 20),
        TEMPERATURE_FALLOFF("climate.temperature.falloff", 2, 1, 10),
        TEMPERATURE_MIN("climate.temperature.min", 0.0, 0.0, 1.0),
        TEMPERATURE_MAX("climate.temperature.max", 0.98, 0.0, 1.0),
        TEMPERATURE_BIAS("climate.temperature.bias", 0.0, -1.0, 1.0),
        MOISTURE_SCALE("climate.moisture.scale", 6, 1, 20),
        MOISTURE_FALLOFF("climate.moisture.falloff", 1, 1, 10),
        MOISTURE_MIN("climate.moisture.min", 0.0, 0.0, 1.0),
        MOISTURE_MAX("climate.moisture.max", 1.0, 0.0, 1.0),
        MOISTURE_BIAS("climate.moisture.bias", 0.0, -1.0, 1.0),
        BIOME_SIZE("climate.biome_size", 185, 50, 2000),
        MACRO_NOISE_SIZE("climate.macro_noise_size", 8, 1, 20),
        BIOME_WARP_SCALE("climate.biome_warp_scale", 150, 1, 500),
        BIOME_WARP_STRENGTH("climate.biome_warp_strength", 80, 1, 500),
        BIOME_EDGE_TYPE("climate.biome_edge.type", 8, 0, 9),
        BIOME_EDGE_SCALE("climate.biome_edge.scale", 24, 1, 500),
        BIOME_EDGE_OCTAVES("climate.biome_edge.octaves", 2, 1, 5),
        BIOME_EDGE_GAIN("climate.biome_edge.gain", 0.5, 0.0, 5.5),
        BIOME_EDGE_LACUNARITY("climate.biome_edge.lacunarity", 2.65, 0.0, 10.5),
        BIOME_EDGE_STRENGTH("climate.biome_edge.strength", 14, 1, 500),
        // --- terrain: general ---
        REGION_SIZE("terrain.region_size", 1350, 125, 5000),
        VERTICAL_SCALE("terrain.vertical_scale", 1.0, 0.01, 1.0),
        TERRAIN_SCALE("terrain.horizontal_scale", 1.175, 0.01, 5.0),
        FANCY_MOUNTAINS("terrain.fancy_mountains", 1, 0, 1),
        // --- terrain: types (weight 0 disables a type) ---
        STEPPE_WEIGHT("terrain.steppe.weight", 1.2, 0.0, 10.0),
        STEPPE_BASE_SCALE("terrain.steppe.base_scale", 1.0, 0.0, 2.0),
        STEPPE_VERTICAL_SCALE("terrain.steppe.vertical_scale", 1.0, 0.0, 10.0),
        STEPPE_HORIZONTAL_SCALE("terrain.steppe.horizontal_scale", 1.0, 0.0, 10.0),
        PLAINS_WEIGHT("terrain.plains.weight", 2.0, 0.0, 10.0),
        PLAINS_BASE_SCALE("terrain.plains.base_scale", 1.0, 0.0, 2.0),
        PLAINS_VERTICAL_SCALE("terrain.plains.vertical_scale", 1.0, 0.0, 10.0),
        PLAINS_HORIZONTAL_SCALE("terrain.plains.horizontal_scale", 1.0, 0.0, 10.0),
        HILLS_WEIGHT("terrain.hills.weight", 3.2, 0.0, 10.0),
        HILLS_BASE_SCALE("terrain.hills.base_scale", 1.0, 0.0, 2.0),
        HILLS_VERTICAL_SCALE("terrain.hills.vertical_scale", 1.15, 0.0, 10.0),
        HILLS_HORIZONTAL_SCALE("terrain.hills.horizontal_scale", 1.0, 0.0, 10.0),
        DALES_WEIGHT("terrain.dales.weight", 1.4, 0.0, 10.0),
        DALES_BASE_SCALE("terrain.dales.base_scale", 1.0, 0.0, 2.0),
        DALES_VERTICAL_SCALE("terrain.dales.vertical_scale", 1.0, 0.0, 10.0),
        DALES_HORIZONTAL_SCALE("terrain.dales.horizontal_scale", 1.0, 0.0, 10.0),
        PLATEAU_WEIGHT("terrain.plateau.weight", 2.4, 0.0, 10.0),
        PLATEAU_BASE_SCALE("terrain.plateau.base_scale", 1.0, 0.0, 2.0),
        PLATEAU_VERTICAL_SCALE("terrain.plateau.vertical_scale", 1.0, 0.0, 10.0),
        PLATEAU_HORIZONTAL_SCALE("terrain.plateau.horizontal_scale", 1.0, 0.0, 10.0),
        BADLANDS_WEIGHT("terrain.badlands.weight", 1.3, 0.0, 10.0),
        BADLANDS_BASE_SCALE("terrain.badlands.base_scale", 1.0, 0.0, 2.0),
        BADLANDS_VERTICAL_SCALE("terrain.badlands.vertical_scale", 1.0, 0.0, 10.0),
        BADLANDS_HORIZONTAL_SCALE("terrain.badlands.horizontal_scale", 1.0, 0.0, 10.0),
        TORRIDONIAN_WEIGHT("terrain.torridonian.weight", 3.6, 0.0, 10.0),
        TORRIDONIAN_BASE_SCALE("terrain.torridonian.base_scale", 1.0, 0.0, 2.0),
        TORRIDONIAN_VERTICAL_SCALE("terrain.torridonian.vertical_scale", 1.2, 0.0, 10.0),
        TORRIDONIAN_HORIZONTAL_SCALE("terrain.torridonian.horizontal_scale", 1.0, 0.0, 10.0),
        MOUNTAINS_WEIGHT("terrain.mountains.weight", 3.8, 0.0, 10.0),
        MOUNTAINS_BASE_SCALE("terrain.mountains.base_scale", 1.0, 0.0, 2.0),
        MOUNTAINS_VERTICAL_SCALE("terrain.mountains.vertical_scale", 1.5, 0.0, 10.0),
        MOUNTAINS_HORIZONTAL_SCALE("terrain.mountains.horizontal_scale", 1.0, 0.0, 10.0),
        VOLCANO_WEIGHT("terrain.volcano.weight", 6.0, 0.0, 10.0),
        VOLCANO_BASE_SCALE("terrain.volcano.base_scale", 1.0, 0.0, 2.0),
        VOLCANO_VERTICAL_SCALE("terrain.volcano.vertical_scale", 1.0, 0.0, 10.0),
        VOLCANO_HORIZONTAL_SCALE("terrain.volcano.horizontal_scale", 1.0, 0.0, 10.0),
        // --- rivers & lakes ---
        RIVER_COUNT("rivers.river_count", 14, 0, 30),
        MAIN_BED_DEPTH("rivers.main.bed_depth", 5, 1, 10),
        MAIN_MIN_BANK_HEIGHT("rivers.main.min_bank_height", 2, 0, 10),
        MAIN_MAX_BANK_HEIGHT("rivers.main.max_bank_height", 6, 1, 10),
        MAIN_BANK_WIDTH("rivers.main.bank_width", 20, 1, 50),
        MAIN_BED_WIDTH("rivers.main.bed_width", 8, 1, 20),
        MAIN_FADE("rivers.main.fade", 0.75, 0.0, 1.0),
        BRANCH_BED_DEPTH("rivers.branch.bed_depth", 4, 1, 10),
        BRANCH_MIN_BANK_HEIGHT("rivers.branch.min_bank_height", 1, 0, 10),
        BRANCH_MAX_BANK_HEIGHT("rivers.branch.max_bank_height", 4, 1, 10),
        BRANCH_BANK_WIDTH("rivers.branch.bank_width", 14, 1, 50),
        BRANCH_BED_WIDTH("rivers.branch.bed_width", 5, 1, 20),
        BRANCH_FADE("rivers.branch.fade", 0.975, 0.0, 1.0),
        LAKE_CHANCE("rivers.lake.chance", 0.6, 0.0, 1.0),
        LAKE_MIN_START_DISTANCE("rivers.lake.min_start_distance", 0.0, 0.0, 1.0),
        LAKE_MAX_START_DISTANCE("rivers.lake.max_start_distance", 0.028, 0.0, 1.0),
        LAKE_DEPTH("rivers.lake.depth", 10, 1, 20),
        LAKE_SIZE_MIN("rivers.lake.size_min", 75, 1, 100),
        LAKE_SIZE_MAX("rivers.lake.size_max", 150, 1, 500),
        LAKE_MIN_BANK_HEIGHT("rivers.lake.min_bank_height", 2, 1, 10),
        LAKE_MAX_BANK_HEIGHT("rivers.lake.max_bank_height", 10, 1, 10),
        WETLAND_CHANCE("rivers.wetland.chance", 0.6, 0.0, 1.0),
        WETLAND_SIZE_MIN("rivers.wetland.size_min", 175, 50, 500),
        WETLAND_SIZE_MAX("rivers.wetland.size_max", 225, 50, 500),
        // --- mountain streams & waterfalls (TerrainForged; flow downhill above sea level into rivers, lakes or the sea) ---
        STREAM_CHANCE("rivers.streams.chance", 0.7, 0.0, 1.0),
        STREAM_MIN_ALTITUDE("rivers.streams.min_altitude", 20, 4, 200),
        STREAM_MAX_LENGTH("rivers.streams.max_length", 384, 64, 2048),
        // --- filters (droplet erosion & smoothing on tiles) ---
        EROSION_DROPLETS("erosion.droplets_per_chunk", 175, 10, 250),
        EROSION_LIFETIME("erosion.droplet_lifetime", 12, 1, 32),
        EROSION_VOLUME("erosion.droplet_volume", 0.648, 0.0, 1.0),
        EROSION_VELOCITY("erosion.droplet_velocity", 0.657, 0.1, 1.0),
        EROSION_RATE("erosion.erosion_rate", 0.5, 0.0, 1.0),
        EROSION_DEPOSIT("erosion.deposit_rate", 0.5, 0.0, 1.0),
        SMOOTHING_ITERATIONS("erosion.smoothing_iterations", 1, 0, 5),
        SMOOTHING_RADIUS("erosion.smoothing_radius", 1.855, 0.0, 5.0),
        SMOOTHING_RATE("erosion.smoothing_rate", 0.916, 0.0, 1.0),
        // --- decoration (TerraForged erosion decorator & natural snow) ---
        EROSION_DECORATOR("decoration.erosion_decorator", 1, 0, 1),
        NATURAL_SNOW("decoration.natural_snow", 1, 0, 1),
        SMOOTH_SNOW("decoration.smooth_snow_layers", 1, 0, 1),
        // --- world spawn chosen on the preview map (per world: never part of presets or the config defaults) ---
        SPAWN_SET("spawn.enabled", 0, 0, 1),
        SPAWN_X("spawn.x", 0, -29_999_000, 29_999_000),
        SPAWN_Z("spawn.z", 0, -29_999_000, 29_999_000);

        public final String id;
        public final double def, min, max;

        Key(String id, double def, double min, double max) {
            this.id = id;
            this.def = def;
            this.min = min;
            this.max = max;
        }

        /** On/off settings (0 or 1). */
        public boolean isToggle() {
            return this == FANCY_MOUNTAINS || this == EROSION_DECORATOR || this == NATURAL_SNOW || this == SMOOTH_SNOW || this == SPAWN_SET;
        }

        /**
         * Settings that belong to one world only (the chosen spawn point). They do not change the terrain, have no
         * slider, and are left out of presets and of the config defaults.
         */
        public boolean isWorldOnly() {
            return this == SPAWN_SET || this == SPAWN_X || this == SPAWN_Z;
        }

        /** Display names for settings that pick one of several options (index = value), or null. */
        public String[] choices() {
            return switch (this) {
                // order of RTF's ContinentType
                case CONTINENT_TYPE -> new String[]{"Multi", "Single", "Multi (improved)", "Experimental", "Infinite"};
                // order of RTF's ClimateSettings.BiomeNoise.EdgeType
                case BIOME_EDGE_TYPE -> new String[]{"Billow", "Cell", "Cell edge", "Constant", "Cubic", "Perlin", "Perlin 2", "Ridge", "Simplex", "Simplex 2"};
                default -> null;
            };
        }

        /** Settings RTF stores as integers (sizes, counts, block distances). */
        public boolean isInteger() {
            return switch (this) {
                case CONTINENT_SCALE, CONTINENT_TYPE, BIOME_EDGE_TYPE, CONTINENT_OCTAVES, SEA_LEVEL, TERRAIN_HEIGHT, TEMPERATURE_SCALE, TEMPERATURE_FALLOFF,
                     MOISTURE_SCALE, MOISTURE_FALLOFF, BIOME_SIZE, MACRO_NOISE_SIZE, BIOME_WARP_SCALE, BIOME_WARP_STRENGTH,
                     BIOME_EDGE_SCALE, BIOME_EDGE_OCTAVES, BIOME_EDGE_STRENGTH, REGION_SIZE, RIVER_COUNT, MAIN_BED_DEPTH,
                     MAIN_MIN_BANK_HEIGHT, MAIN_MAX_BANK_HEIGHT, MAIN_BANK_WIDTH, MAIN_BED_WIDTH, BRANCH_BED_DEPTH,
                     BRANCH_MIN_BANK_HEIGHT, BRANCH_MAX_BANK_HEIGHT, BRANCH_BANK_WIDTH, BRANCH_BED_WIDTH, LAKE_DEPTH,
                     LAKE_SIZE_MIN, LAKE_SIZE_MAX, LAKE_MIN_BANK_HEIGHT, LAKE_MAX_BANK_HEIGHT, WETLAND_SIZE_MIN,
                     WETLAND_SIZE_MAX, EROSION_DROPLETS, EROSION_LIFETIME, SMOOTHING_ITERATIONS, SPAWN_X, SPAWN_Z,
                     STREAM_MIN_ALTITUDE, STREAM_MAX_LENGTH -> true;
                default -> isToggle();
            };
        }
    }

    private static final TerrainSettings DEFAULT = new TerrainSettings(new double[0]);
    private final double[] values;
    private final int hash;

    private TerrainSettings(double[] raw) {
        Key[] keys = Key.values();
        this.values = new double[keys.length];
        for (Key k : keys) {
            double v = k.ordinal() < raw.length && Double.isFinite(raw[k.ordinal()]) ? raw[k.ordinal()] : k.def;
            v = Math.max(k.min, Math.min(k.max, v));
            values[k.ordinal()] = k.isInteger() ? Math.round(v) : v;
        }
        this.hash = Arrays.hashCode(values);
    }

    public static TerrainSettings defaults() {
        return DEFAULT;
    }

    public static TerrainSettings fromMap(Map<String, ? extends Number> map) {
        Key[] keys = Key.values();
        double[] raw = new double[keys.length];
        for (Key k : keys) {
            Number n = map.get(k.id);
            raw[k.ordinal()] = n == null ? k.def : n.doubleValue();
        }
        return new TerrainSettings(raw);
    }

    public Map<String, Double> toMap() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Key k : Key.values()) out.put(k.id, values[k.ordinal()]);
        return out;
    }

    /** The settings without world-only keys, for presets and config files. */
    public Map<String, Double> toPresetMap() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Key k : Key.values()) if (!k.isWorldOnly()) out.put(k.id, values[k.ordinal()]);
        return out;
    }

    /** True if a spawn point was chosen on the preview map. */
    public boolean hasSpawn() {
        return get(Key.SPAWN_SET) >= 0.5;
    }

    public int spawnX() {
        return (int) get(Key.SPAWN_X);
    }

    public int spawnZ() {
        return (int) get(Key.SPAWN_Z);
    }

    public TerrainSettings withSpawn(int x, int z) {
        return with(Key.SPAWN_SET, 1).with(Key.SPAWN_X, x).with(Key.SPAWN_Z, z);
    }

    public TerrainSettings withoutSpawn() {
        return with(Key.SPAWN_SET, 0).with(Key.SPAWN_X, 0).with(Key.SPAWN_Z, 0);
    }

    /** These settings with the spawn point (or the lack of one) copied from {@code other}. */
    public TerrainSettings withSpawnOf(TerrainSettings other) {
        return other.hasSpawn() ? withSpawn(other.spawnX(), other.spawnZ()) : withoutSpawn();
    }

    public TerrainSettings with(Key key, double value) {
        double[] copy = values.clone();
        copy[key.ordinal()] = value;
        return new TerrainSettings(copy);
    }

    public double get(Key key) {
        return values[key.ordinal()];
    }

    public int seaLevel() {
        return (int) Math.round(get(Key.SEA_LEVEL));
    }

    /** Highest terrain the engine can produce (blocks). */
    public double maxElevation() {
        return get(Key.TERRAIN_HEIGHT);
    }

    /** Keeps the terrain inside the dimension: the terrain height scale must leave room below the top slide. */
    public TerrainSettings fitToWorld(int minY, int maxYExclusive) {
        TerrainSettings s = this;
        double cap = maxYExclusive - 48;
        if (s.get(Key.TERRAIN_HEIGHT) > cap) s = s.with(Key.TERRAIN_HEIGHT, cap);
        if (s.get(Key.SEA_LEVEL) < minY + 16) s = s.with(Key.SEA_LEVEL, minY + 16);
        if (s.get(Key.SEA_LEVEL) > s.get(Key.TERRAIN_HEIGHT) - 32) s = s.with(Key.SEA_LEVEL, s.get(Key.TERRAIN_HEIGHT) - 32);
        return s;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TerrainSettings t && Arrays.equals(values, t.values);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "TerrainSettings" + toMap();
    }
}
