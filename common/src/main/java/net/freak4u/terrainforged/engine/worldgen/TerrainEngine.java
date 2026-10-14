package net.freak4u.terrainforged.engine.worldgen;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;

/**
 * The shared, loader-independent terrain engine. One instance per (seed, settings, min Y); thread-safe.
 *
 * <pre>
 *  ReTerraForged 1.20.1 pipeline: continents -> terrain regions (blended types, mountain chains, volcanoes)
 *  -> river / lake / wetland network carved into the heightmap -> climate (temperature, moisture, biome regions)
 *  -> droplet erosion + smoothing on tiles -> vanilla multi-noise coordinates
 * </pre>
 * Rivers, lakes and oceans are carved below sea level and filled by vanilla's aquifers, exactly as in RTF; surface
 * materials, caves, structures and features are vanilla's, plus TerrainForged's erosion/snow decorator on the
 * Minecraft side.
 */
public final class TerrainEngine {
    private final long seed;
    private final TerrainSettings settings;
    private final RtfTerrain terrain;
    private final int minHeight;
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    public TerrainEngine(long seed, TerrainSettings settings) {
        this(seed, settings, -64);
    }

    public TerrainEngine(long seed, TerrainSettings settings, int minY) {
        this.seed = seed;
        this.settings = settings;
        this.terrain = new RtfTerrain(seed, settings);
        this.minHeight = minY + 8;
    }

    public long seed() { return seed; }
    public TerrainSettings settings() { return settings; }
    public RtfTerrain terrain() { return terrain; }

    private volatile StreamNetwork streams;

    /** Mountain streams and waterfalls for this world (see {@link StreamNetwork}). */
    public StreamNetwork streams() {
        StreamNetwork s = streams;
        if (s == null) {
            synchronized (this) {
                s = streams;
                if (s == null) streams = s = new StreamNetwork(terrain, seed, settings);
            }
        }
        return s;
    }

    private volatile SurfaceDecorator decorator;

    /** Shared decorator logic (erosion / snow) for this world. */
    public SurfaceDecorator surfaceDecorator() {
        SurfaceDecorator d = decorator;
        if (d == null) decorator = d = new SurfaceDecorator(this);
        return d;
    }

    public boolean decorateErosion() { return settings.get(TerrainSettings.Key.EROSION_DECORATOR) >= 0.5; }
    public boolean decorateSnow() { return settings.get(TerrainSettings.Key.NATURAL_SNOW) >= 0.5; }
    public boolean smoothSnow() { return settings.get(TerrainSettings.Key.SMOOTH_SNOW) >= 0.5; }

    /** Plate id at a position (for tools and tests). */
    public long continentAt(int x, int z) {
        return terrain.continentAt(x, z);
    }

    /**
     * Returns the cached sample for this column (computing it if needed). The returned object is owned by a
     * per-thread cache and may be overwritten by the next call on the same thread: read what you need at once.
     */
    public ColumnSample sample(int x, int z) {
        Scratch s = scratch.get();
        int slot = (int) ((x * 0x9E3779B1L + z * 0x85EBCA77L) >>> 7) & (Scratch.SIZE - 1);
        ColumnSample c = s.cache[slot];
        if (c != null && c.x == x && c.z == z) {
            s.hits++;
            return c;
        }
        s.misses++;
        if (c == null) c = s.cache[slot] = new ColumnSample();
        compute(x, z, s, c);
        return c;
    }

    /** Uncached computation into a caller-owned sample (used by tests and tools). */
    public ColumnSample compute(int x, int z) {
        ColumnSample c = new ColumnSample();
        compute(x, z, new Scratch(), c);
        return c;
    }

    private void compute(int x, int z, Scratch s, ColumnSample c) {
        RtfTerrain.TileData td = s.lastTile;
        if (td == null || x < td.x0 || z < td.z0 || x >= td.x0 + RtfTerrain.TILE_BLOCKS || z >= td.z0 + RtfTerrain.TILE_BLOCKS) {
            td = s.lastTile = terrain.tileAt(x, z);
        }
        int i = td.index(x, z);
        c.x = x;
        c.z = z;
        c.height = Math.max(minHeight, td.height[i]);
        c.heightRaw = td.height[i] / terrain.levels().worldHeight;
        c.gradient = td.gradient[i];
        c.sediment = td.sediment[i];
        c.riverMask = td.riverMask[i];
        c.flags = td.flags[i];
        c.vContinentalness = td.continentalness[i];
        c.vErosion = td.erosion[i];
        c.vWeirdness = td.weirdness[i];
        c.vTemperature = td.temperature[i];
        c.vHumidity = td.moisture[i];
    }

    /** Raw (un-eroded) column, as RTF computes it outside of tiles; for tools and spawn search. */
    public ColumnSample preview(int x, int z) {
        RtfTerrain.Sample r = new RtfTerrain.Sample();
        terrain.sampleRaw(x, z, r);
        ColumnSample c = new ColumnSample();
        c.x = x;
        c.z = z;
        copy(r, c);
        return c;
    }

    void copy(RtfTerrain.Sample r, ColumnSample c) {
        c.height = Math.max(minHeight, r.height);
        c.heightRaw = r.heightRaw;
        c.gradient = r.gradient;
        c.sediment = r.sediment;
        c.riverMask = r.riverMask;
        c.flags = r.flags;
        c.vContinentalness = r.continentalness;
        c.vErosion = r.erosion;
        c.vWeirdness = r.weirdness;
        c.vTemperature = r.temperature;
        c.vHumidity = r.moisture;
    }

    /** RTF's zoomed preview tile: 128x128 columns spaced {@code blocksPerPixel} apart around a centre. */
    public ColumnSample[] previewTile(int centerX, int centerZ, float blocksPerPixel) {
        int n = RtfTerrain.TILE_BLOCKS;
        RtfTerrain.Sample[] raw = new RtfTerrain.Sample[n * n];
        for (int k = 0; k < raw.length; k++) raw[k] = new RtfTerrain.Sample();
        terrain.preview(centerX, centerZ, blocksPerPixel, raw);
        ColumnSample[] out = new ColumnSample[n * n];
        for (int k = 0; k < raw.length; k++) {
            ColumnSample c = out[k] = new ColumnSample();
            copy(raw[k], c);
        }
        return out;
    }

    public long[] cacheStats() {
        Scratch s = scratch.get();
        long[] t = terrain.stats();
        return new long[]{s.hits, s.misses, t[0], t[1], t[2]};
    }

    private static final class Scratch {
        static final int SIZE = 4096;
        final ColumnSample[] cache = new ColumnSample[SIZE];
        long hits, misses;
        RtfTerrain.TileData lastTile;
    }
}
