package net.freak4u.terrainforged.engine.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;

/**
 * TerrainForged mountain streams, waterfalls and mountain lakes: streams that start on high ground and flow downhill,
 * above sea level, into a river, lake, wetland or the sea.
 *
 * <p>Since 0.7.0 every stream has somewhere to go:
 * <ul>
 *   <li>it follows the valley floor: the path is traced at 2-block steps by steepest descent over a slightly smoothed
 *       copy of RTF's height field, so it runs down the middle of valleys instead of zig-zagging over every bump;</li>
 *   <li>when it runs into a hollow it fills it like real water: the hollow becomes a small mountain lake (tarn) whose
 *       surface is just below the lowest point of its rim, and the stream leaves through that low point and goes on;</li>
 *   <li>a stream that cannot reach a river, lake or the sea within its maximum length is not generated at all.</li>
 * </ul>
 * Why this does not bring back 0.2's floating lakes: the water level only goes down along a stream (it is flat across
 * a lake), and when it is placed in the world ({@link StreamCarver}) every water column is lowered until all its
 * non-stream neighbours rise at least to the water surface, so water is held by real terrain on every side. Where
 * the level drops faster than the channel, water pours over the edge - rapids and waterfalls. Everything here is a
 * pure function of seed, settings and position, so chunks generated in any order agree. Made by Freak4U.
 */
public final class StreamNetwork {
    /** Source cells: at most one stream starts in each CELL x CELL square. */
    public static final int CELL = 48;
    /** Path resolution in blocks. */
    public static final int STEP = 2;
    /** A stream never notches deeper than this below the ground; deeper columns are left dry. */
    public static final int MAX_CUT = 10;
    public static final int NONE = Integer.MIN_VALUE;
    /** A hollow that needs more 2x2-block cells than this to fill is too big: the stream is not generated. */
    static final int MAX_LAKE_CELLS = 900;
    /** Hollows with fewer water cells than this are just a dip in the stream, not a lake. */
    static final int MIN_LAKE_CELLS = 10;
    /** How far (blocks) a lake or its outlet path may reach beyond the stream's length budget. */
    static final int LAKE_REACH = 160;
    /** Deepest pool a stream forms in a dip along its channel; deeper dips get their downstream lip notched. */
    public static final int MAX_POOL = 10;
    private static final int CACHE_SIZE = 32768;
    private static final Object NO_PATH = new Object();
    private static final int[][] DIRS = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};

    /**
     * One stream: points every ~2 blocks from source to mouth with the water surface (block y) at each point, plus the
     * blocks of the mountain lakes it fills on the way.
     */
    public static final class Path {
        public final int[] x, z, water;
        /** true where the point lies in one of the stream's lakes */
        public final boolean[] lake;
        public final int length;
        public final boolean reachesWater;
        /** lake blocks (position, water surface); empty if the stream fills no lake */
        public final int[] lakeX, lakeZ, lakeWater;
        public final int minX, minZ, maxX, maxZ;

        Path(int[] x, int[] z, int[] water, boolean[] lake, int n, boolean reachesWater, int[] lx, int[] lz, int[] lw, int ln) {
            this.x = Arrays.copyOf(x, n);
            this.z = Arrays.copyOf(z, n);
            this.water = Arrays.copyOf(water, n);
            this.lake = Arrays.copyOf(lake, n);
            this.length = n;
            this.reachesWater = reachesWater;
            this.lakeX = Arrays.copyOf(lx, ln);
            this.lakeZ = Arrays.copyOf(lz, ln);
            this.lakeWater = Arrays.copyOf(lw, ln);
            int a = Integer.MAX_VALUE, b = Integer.MAX_VALUE, c = Integer.MIN_VALUE, d = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                a = Math.min(a, x[i]); b = Math.min(b, z[i]); c = Math.max(c, x[i]); d = Math.max(d, z[i]);
            }
            for (int i = 0; i < ln; i++) {
                a = Math.min(a, lx[i] - 2); b = Math.min(b, lz[i] - 2); c = Math.max(c, lx[i] + 2); d = Math.max(d, lz[i] + 2);
            }
            minX = a;
            minZ = b;
            maxX = c;
            maxZ = d;
        }

        /** Streams widen as they go: 1 block near the source, then 2, then 3. */
        public float halfWidth(int i) {
            int s = i * STEP;
            return s < 120 ? 0.75F : s < 360 ? 1.25F : 1.75F;
        }

        /** Water depth (blocks) at a point: 1 for small streams, 2 further down. */
        public int depth(int i) {
            return halfWidth(i) > 1 ? 2 : 1;
        }

        boolean overlaps(int ax, int az, int bx, int bz) {
            return maxX + 2 >= ax && minX - 2 <= bx && maxZ + 2 >= az && minZ - 2 <= bz;
        }
    }

    private final RtfTerrain terrain;
    private final long seed;
    private final int seaLevel, minAltitude, maxSteps;
    private final double chance;
    private final ThreadLocal<RtfTerrain.Sample> scratch = ThreadLocal.withInitial(RtfTerrain.Sample::new);
    // one trace per cell even when several chunk-generation threads ask at once; cleared when it grows too big
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<Object>> cache =
        new java.util.concurrent.ConcurrentHashMap<>();

    public StreamNetwork(RtfTerrain terrain, long seed, TerrainSettings settings) {
        this.terrain = terrain;
        this.seed = seed ^ 0x5DEECE66DL * 31;
        this.seaLevel = settings.seaLevel();
        this.chance = settings.get(TerrainSettings.Key.STREAM_CHANCE);
        this.minAltitude = (int) settings.get(TerrainSettings.Key.STREAM_MIN_ALTITUDE);
        this.maxSteps = (int) settings.get(TerrainSettings.Key.STREAM_MAX_LENGTH) / STEP;
    }

    public boolean enabled() {
        return chance > 0;
    }

    public int maxLength() {
        return maxSteps * STEP;
    }

    // ------------------------------------------------------------------------------------------------ queries

    /** All streams whose bounding box (plus 2 blocks) touches the area [minX, maxX] x [minZ, maxZ]. */
    public List<Path> pathsNear(int minX, int minZ, int maxX, int maxZ) {
        List<Path> out = new ArrayList<>();
        if (!enabled()) return out;
        int reach = maxLength() + LAKE_REACH;
        int c0x = Math.floorDiv(minX - reach, CELL), c1x = Math.floorDiv(maxX + reach, CELL);
        int c0z = Math.floorDiv(minZ - reach, CELL), c1z = Math.floorDiv(maxZ + reach, CELL);
        for (int cz = c0z; cz <= c1z; cz++) {
            for (int cx = c0x; cx <= c1x; cx++) {
                Path p = pathFrom(cx, cz);
                if (p != null && p.overlaps(minX, minZ, maxX, maxZ)) out.add(p);
            }
        }
        return out;
    }

    /** Streams that start inside the area (cheaper than {@link #pathsNear}; for preview maps). */
    public List<Path> pathsStartingIn(int minX, int minZ, int maxX, int maxZ) {
        List<Path> out = new ArrayList<>();
        if (!enabled()) return out;
        for (int cz = Math.floorDiv(minZ, CELL); cz <= Math.floorDiv(maxZ, CELL); cz++) {
            for (int cx = Math.floorDiv(minX, CELL); cx <= Math.floorDiv(maxX, CELL); cx++) {
                Path p = pathFrom(cx, cz);
                if (p != null) out.add(p);
            }
        }
        return out;
    }

    /**
     * Water surface (block y of the top water block) for every column of the area, or {@link #NONE}; {@code depth}
     * (same layout, may be null) receives the water depth. Where streams overlap the lower level wins.
     */
    public int[] waterLevels(int x0, int z0, int w, int h, int[] depth) {
        int[] out = new int[w * h];
        Arrays.fill(out, NONE);
        for (Path p : pathsNear(x0, z0, x0 + w - 1, z0 + h - 1)) rasterize(p, x0, z0, w, h, out, depth);
        return out;
    }

    static void rasterize(Path p, int x0, int z0, int w, int h, int[] out, int[] depth) {
        for (int i = 0; i < p.length; i++) {
            int j = Math.min(i + 1, p.length - 1);
            float hw = p.halfWidth(i);
            int ax = p.x[i], az = p.z[i], bx = p.x[j], bz = p.z[j];
            int lx = (int) Math.floor(Math.min(ax, bx) - hw), hx = (int) Math.ceil(Math.max(ax, bx) + hw);
            int lz = (int) Math.floor(Math.min(az, bz) - hw), hz = (int) Math.ceil(Math.max(az, bz) + hw);
            lx = Math.max(lx, x0);
            lz = Math.max(lz, z0);
            hx = Math.min(hx, x0 + w - 1);
            hz = Math.min(hz, z0 + h - 1);
            if (lx > hx || lz > hz) continue;
            double vx = bx - ax, vz = bz - az, vv = vx * vx + vz * vz;
            for (int z = lz; z <= hz; z++) {
                for (int x = lx; x <= hx; x++) {
                    double t = vv == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / vv));
                    double dx = ax + t * vx - x, dz = az + t * vz - z;
                    if (dx * dx + dz * dz > hw * hw) continue;
                    int k = (z - z0) * w + (x - x0);
                    int level = t < 0.5 ? p.water[i] : p.water[j];
                    if (out[k] == NONE || level < out[k]) out[k] = level;
                    if (depth != null) depth[k] = Math.max(depth[k], p.depth(i));
                }
            }
        }
        // lakes: one entry per block
        for (int i = 0; i < p.lakeX.length; i++) {
            int x = p.lakeX[i], z = p.lakeZ[i];
            if (x < x0 || z < z0 || x >= x0 + w || z >= z0 + h) continue;
            int k = (z - z0) * w + (x - x0);
            if (out[k] == NONE || p.lakeWater[i] < out[k]) out[k] = p.lakeWater[i];
            if (depth != null) depth[k] = Math.max(depth[k], 2);
        }
    }

    // ------------------------------------------------------------------------------------------------ sources & tracing

    /** The stream starting in source cell (cx, cz), or null. Cached; thread-safe. */
    public Path pathFrom(int cx, int cz) {
        long key = RtfTerrain.pack(cx, cz);
        java.util.concurrent.CompletableFuture<Object> f = cache.get(key);
        if (f == null) {
            if (cache.size() > CACHE_SIZE) cache.clear();
            java.util.concurrent.CompletableFuture<Object> mine = new java.util.concurrent.CompletableFuture<>();
            f = cache.putIfAbsent(key, mine);
            if (f == null) {
                f = mine;
                try {
                    Path p = trace(cx, cz);
                    mine.complete(p == null ? NO_PATH : p);
                } catch (RuntimeException ex) {
                    cache.remove(key, mine);
                    mine.completeExceptionally(ex);
                    throw ex;
                }
            }
        }
        Object v = f.join();
        return v == NO_PATH ? null : (Path) v;
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        return h ^ (h >>> 33);
    }

    private static boolean isWater(RtfTerrain.Sample s) {
        return (s.flags & (RtfTerrain.FLAG_RIVER | RtfTerrain.FLAG_LAKE | RtfTerrain.FLAG_WETLAND | RtfTerrain.FLAG_OCEAN)) != 0;
    }

    // Raw RTF heights, shared by all traces: neighbouring streams run down the same valleys and fill the same
    // hollows, so most samples are reused. Tiles of 128x128 blocks, filled lazily (NaN = not yet sampled; concurrent
    // fills write the same value). Cleared when it grows too big.
    private static final int GRID_TILE = 128;
    private final java.util.concurrent.ConcurrentHashMap<Long, float[]> grid = new java.util.concurrent.ConcurrentHashMap<>();

    private float[] gridTile(int x, int z) {
        long key = RtfTerrain.pack(x >> 7, z >> 7);
        float[] t = grid.get(key);
        if (t == null) {
            if (grid.size() > 384) grid.clear(); // 384 tiles = ~50 MB
            float[] fresh = new float[GRID_TILE * GRID_TILE * 2];
            Arrays.fill(fresh, Float.NaN);
            t = grid.putIfAbsent(key, fresh);
            if (t == null) t = fresh;
        }
        return t;
    }

    /** {height, water} at a block; water is 1 for rivers, lakes, wetland, ocean or ground at the sea. */
    private float gridValue(int x, int z, boolean water) {
        float[] t = gridTile(x, z);
        int i = ((z & 127) * GRID_TILE + (x & 127)) * 2;
        float hgt = t[i];
        if (Float.isNaN(hgt)) {
            RtfTerrain.Sample sample = scratch.get();
            terrain.sampleRaw(x, z, sample);
            t[i + 1] = isWater(sample) || sample.height <= seaLevel + 0.5 ? 1 : 0;
            t[i] = hgt = (float) sample.height;
        }
        return water ? t[i + 1] : hgt;
    }

    /** Height field for one trace (all positions are even). */
    private final class Field {
        double height(int x, int z) {
            return gridValue(x, z, false);
        }

        boolean water(int x, int z) {
            return gridValue(x, z, true) > 0;
        }

        double block(int x, int z) {
            return height(x, z);
        }

        /** Height lightly averaged over +-4 blocks: valley floors stay low, small bumps and pits fade. */
        double smooth(int x, int z) {
            return 0.4 * height(x, z) + 0.15 * (height(x + 4, z) + height(x - 4, z) + height(x, z + 4) + height(x, z - 4));
        }
    }

    /** Growing arrays for a path under construction. */
    private static final class Builder {
        int[] x = new int[256], z = new int[256], w = new int[256];
        boolean[] lake = new boolean[256];
        int n;
        int[] lx = new int[64], lz = new int[64], lw = new int[64];
        int ln;

        void add(int px, int pz, int water, boolean inLake) {
            if (n == x.length) {
                x = Arrays.copyOf(x, n * 2); z = Arrays.copyOf(z, n * 2); w = Arrays.copyOf(w, n * 2); lake = Arrays.copyOf(lake, n * 2);
            }
            x[n] = px; z[n] = pz; w[n] = water; lake[n] = inLake; n++;
        }

        void addLake(int px, int pz, int water) {
            if (ln == lx.length) {
                lx = Arrays.copyOf(lx, ln * 2); lz = Arrays.copyOf(lz, ln * 2); lw = Arrays.copyOf(lw, ln * 2);
            }
            lx[ln] = px; lz[ln] = pz; lw[ln] = water; ln++;
        }
    }

    Path trace(int cx, int cz) {
        long h = mix(seed + cx * 0x9E3779B97F4A7C15L + cz * 0xC2B2AE3D27D4EB4FL);
        double roll = unit(h);
        if (roll >= chance) return null; // most cells are rejected without sampling the terrain
        int x = cx * CELL + 8 + (int) (unit(mix(h + 1)) * (CELL - 16)) & ~1;
        int z = cz * CELL + 8 + (int) (unit(mix(h + 2)) * (CELL - 16)) & ~1;
        RtfTerrain.Sample s = scratch.get();
        terrain.sampleRaw(x, z, s);
        if (isWater(s) || (s.flags & (RtfTerrain.FLAG_VOLCANO | RtfTerrain.FLAG_VOLCANO_PIPE)) != 0) return null;
        double above = s.height - seaLevel;
        if (above < minAltitude) return null;
        // more springs on high, wet ground
        double altitudeFactor = Math.min(1.0, 0.35 + (above - minAltitude) / 50.0);
        double wetFactor = Math.min(1.0, 0.7 + 0.6 * s.moisture);
        if (roll >= chance * altitudeFactor * wetFactor) return null;

        Field f = new Field();
        Builder b = new Builder();
        Set<Long> visited = new HashSet<>();
        int water = (int) Math.floor(f.height(x, z));
        b.add(x, z, water, false);
        visited.add(RtfTerrain.pack(x, z));
        boolean reached = false;
        int revisits = 0;
        while (b.n < maxSteps) {
            // steepest descent of the smoothed field over the 8 neighbours 2 blocks away
            double here = f.smooth(x, z);
            int bx = 0, bz = 0;
            double bestSlope = 0;
            for (int[] d : DIRS) {
                int nx = x + d[0], nz = z + d[1];
                if (visited.contains(RtfTerrain.pack(nx, nz))) continue;
                double slope = (here - f.smooth(nx, nz)) / (d[0] != 0 && d[1] != 0 ? 2.83 : 2.0);
                if (slope > bestSlope) {
                    bestSlope = slope;
                    bx = nx;
                    bz = nz;
                }
            }
            List<int[]> steps = new ArrayList<>();
            Set<Long> lakeArea = null;
            int lakeLevel = NONE;
            if (bestSlope > 0.01) {
                steps.add(new int[]{bx, bz});
            } else if (f.height(x, z) <= seaLevel + 3) {
                // flat lowland just above the sea: the stream has arrived (it soaks into the coastal flats)
                reached = true;
                break;
            } else {
                // a hollow: fill it and leave through the lowest point of its rim
                Set<Long> blocked = new HashSet<>();
                for (int k = 0; k < b.n - 6; k++) blocked.add(RtfTerrain.pack(b.x[k], b.z[k]));
                FillResult fill = fill(f, x, z, blocked);
                if (fill == null) return null; // too big to fill: this stream would not reach anywhere
                // the lake may not rise above the water upstream of it (water never flows uphill)
                int k0 = b.n - 1;
                while (k0 >= 0 && f.height(b.x[k0], b.z[k0]) < fill.level + 1) k0--;
                lakeLevel = k0 >= 0 ? Math.min(fill.level, b.w[k0]) : fill.level;
                java.util.LinkedHashSet<Long> area = lakeArea(f, fill.cells, lakeLevel);
                lakeLevel = Math.min(lakeLevel, shoreLevel(f, area, lakeLevel));
                if (lakeLevel < water) {
                    lakeLevel = NONE; // it fills to below the stream's own level: just a dip in the channel, no lake
                } else {
                    for (int k = b.n - 1; k > k0; k--) {
                        b.w[k] = lakeLevel;
                        b.lake[k] = true;
                    }
                    water = lakeLevel;
                    int[] shore = lakeBlocks(area, f, lakeLevel);
                    if (shore != null) for (int k = 0; k + 1 < shore.length; k += 2) b.addLake(shore[k], shore[k + 1], lakeLevel);
                }
                if (fill.closed) {
                    // ends in its own lake (if it really is one), which is where this stream flows
                    if (lakeLevel == NONE || b.ln == 0) break;
                    reached = true;
                    break;
                }
                lakeArea = fill.area;
                steps.addAll(fill.route);
            }
            boolean stop = false;
            for (int[] st : steps) {
                int nx = st[0], nz = st[1];
                if (!visited.add(RtfTerrain.pack(nx, nz)) && ++revisits > 24) return null; // wandering in circles
                double g = f.height(nx, nz);
                int ground = (int) Math.floor(g);
                boolean inLake = lakeLevel != NONE && g < lakeLevel + 1;
                if (!inLake) {
                    if (ground - water > MAX_CUT) return null; // would need a gorge: not a natural stream
                    water = Math.min(water, ground);
                }
                x = nx;
                z = nz;
                if (f.water(nx, nz)) {
                    water = Math.min(water, seaLevel - 1);
                    b.add(x, z, water, false);
                    reached = true;
                    stop = true;
                    break;
                }
                b.add(x, z, water, inLake);
            }
            if (stop) break;
            if (lakeArea != null) visited.addAll(lakeArea); // never wander back into a hollow already filled
        }
        if (!reached || b.n < 8) return null; // only streams that flow somewhere
        fillProfile(f, b);
        terrace(b.w, b.lake, b.n, h);
        return new Path(b.x, b.z, b.w, b.lake, b.n, true, b.lx, b.lz, b.lw, b.ln);
    }

    /**
     * The lake's blocks: the 3x3 squares around its cells whose ground lies below the surface. Squares of cells
     * whose eight grid neighbours are all lake cells too are taken whole (deep inside the lake); only the edge is
     * checked block by block.
     */
    private static java.util.LinkedHashSet<Long> lakeArea(Field f, List<int[]> cells, int level) {
        Set<Long> wet = new HashSet<>();
        for (int[] c : cells) if (f.height(c[0], c[1]) < level + 1) wet.add(RtfTerrain.pack(c[0], c[1]));
        java.util.LinkedHashSet<Long> blocks = new java.util.LinkedHashSet<>();
        for (int[] c : cells) {
            if (!wet.contains(RtfTerrain.pack(c[0], c[1]))) continue;
            boolean interior = true;
            for (int[] d : DIRS) interior &= wet.contains(RtfTerrain.pack(c[0] + d[0], c[1] + d[1]));
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    long k = RtfTerrain.pack(c[0] + dx, c[1] + dz);
                    if (!blocks.contains(k) && (interior || f.block(c[0] + dx, c[1] + dz) < level + 1)) blocks.add(k);
                }
            }
        }
        return blocks;
    }

    /**
     * Highest lake surface the shoreline really holds, checked block by block: one block below the lowest ground
     * next to the lake (so at steep rims the lake never overhangs a drop).
     */
    private static int shoreLevel(Field f, java.util.LinkedHashSet<Long> blocks, int level) {
        int lowest = level;
        for (long k : blocks) {
            int x = (int) (k >> 32), z = (int) k;
            int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
            for (int[] n : nb) {
                if (blocks.contains(RtfTerrain.pack(n[0], n[1]))) continue;
                lowest = Math.min(lowest, (int) Math.floor(f.block(n[0], n[1])) - 1);
            }
        }
        return lowest;
    }

    /**
     * {x0, z0, x1, z1, ...} of the lake's blocks at the (possibly lowered) surface, or null if too small for a lake.
     * Blocks whose ground now rises to the surface are shore; lowering never uncovers new lake blocks.
     */
    private static int[] lakeBlocks(java.util.LinkedHashSet<Long> area, Field f, int level) {
        List<Long> keep = new ArrayList<>();
        for (long k : area) if (f.block((int) (k >> 32), (int) k) < level + 1) keep.add(k);
        if (keep.size() < MIN_LAKE_CELLS * 4) return null;
        int[] out = new int[keep.size() * 2];
        int i = 0;
        for (long k : keep) {
            out[i++] = (int) (k >> 32);
            out[i++] = (int) k;
        }
        return out;
    }

    private static final class FillResult {
        int level;
        boolean closed; // no outlet within MAX_LAKE_CELLS: the stream ends in this lake
        final Set<Long> area = new HashSet<>(); // every cell the fill covered (the stream must not wander back in)
        final List<int[]> cells = new ArrayList<>(); // lake cells (below the level)
        final List<int[]> route = new ArrayList<>(); // path from the start to the outlet, excluding the start
    }

    /**
     * Priority flood from (x, z) on the 2-block grid: cells are taken lowest first; the highest height taken so far is
     * the spill level; the first cell clearly lower than it lies beyond the rim - the outlet. The lake surface is the
     * block below the spill height. Null if the hollow is too big.
     */
    private FillResult fill(Field f, int x, int z, Set<Long> blocked) {
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, c) -> Double.compare(a[0], c[0]));
        Map<Long, Long> parent = new HashMap<>();
        long start = RtfTerrain.pack(x, z);
        parent.put(start, start);
        queue.add(new double[]{f.height(x, z), x, z});
        double spill = f.height(x, z);
        List<long[]> taken = new ArrayList<>();
        while (!queue.isEmpty()) {
            double[] c = queue.poll();
            int px = (int) c[1], pz = (int) c[2];
            long key = RtfTerrain.pack(px, pz);
            // the outlet: the first cell below the lake surface that lies beyond the rim (or real water)
            if ((spill > f.height(x, z) + 1 && c[0] < Math.floor(spill) - 1) || (f.water(px, pz) && key != start)) {
                // outlet found: route back through the parents
                FillResult r = new FillResult();
                r.level = (int) Math.floor(spill) - 1;
                for (long[] t : taken) {
                    if (f.height((int) t[0], (int) t[1]) < r.level + 1) r.cells.add(new int[]{(int) t[0], (int) t[1]});
                    r.area.add(RtfTerrain.pack((int) t[0], (int) t[1]));
                }
                List<int[]> back = new ArrayList<>();
                for (long k = key; k != start; k = parent.get(k)) back.add(new int[]{(int) (k >> 32), (int) k});
                for (int i = back.size() - 1; i >= 0; i--) r.route.add(back.get(i));
                return r;
            }
            spill = Math.max(spill, c[0]);
            taken.add(new long[]{px, pz});
            if (taken.size() > MAX_LAKE_CELLS) {
                // no outlet nearby: a closed mountain lake. Everything taken lies at or below the spill height and
                // every cell around it (still queued) at or above it, so a surface just below the spill is held in.
                FillResult r = new FillResult();
                r.closed = true;
                r.level = (int) Math.floor(Math.min(spill, queue.isEmpty() ? spill : queue.peek()[0])) - 1;
                for (long[] t : taken) if (f.height((int) t[0], (int) t[1]) < r.level + 1) r.cells.add(new int[]{(int) t[0], (int) t[1]});
                return r;
            }
            for (int[] d : DIRS) {
                int nx = px + d[0], nz = pz + d[1];
                long nk = RtfTerrain.pack(nx, nz);
                if (parent.containsKey(nk) || blocked.contains(nk)) continue; // never flow back up the own stream
                parent.put(nk, key);
                queue.add(new double[]{f.height(nx, nz), nx, nz});
            }
        }
        return null;
    }

    /**
     * The water surface along the stream, worked out from the mouth upwards: at each point it is the ground there or
     * the surface just downstream, whichever is higher. Water therefore sits level with the ground on every slope (a
     * full channel, not a trench), and where the path crosses a small dip it fills it into a pool up to the next lip,
     * like real water (at most {@link #MAX_POOL} deep; the lip of a deeper dip is notched). Lakes keep their own
     * surface, and a lake's outlet is notched down to it. The result never rises downstream.
     */
    private static void fillProfile(Field f, Builder b) {
        for (int i = b.n - 2; i >= 0; i--) {
            if (b.lake[i]) continue;
            int ground = (int) Math.floor(f.height(b.x[i], b.z[i]));
            b.w[i] = Math.max(ground, Math.min(b.w[i + 1], ground + MAX_POOL)); // deeper dips: notch the lip instead
        }
        // below a lake the water cannot be higher than the lake: its outlet is notched down to the lake surface
        for (int i = 1; i < b.n; i++) b.w[i] = Math.min(b.w[i], b.w[i - 1]);
    }

    /**
     * Pools and drops: runs of the stream whose water level falls by at most a step are flattened to the level at
     * their downstream end (water only ever gets lower, never above the ground; a run's upper end sits up to one step
     * below the ground). Steps are 1 block on gentle ground and 2-3 on steep ground, so channels stay nearly full;
     * steeper ground drops faster than that and makes the bigger falls on its own. Lake stretches are left alone.
     */
    static void terrace(int[] w, boolean[] lake, int n, long hash) {
        int i = 0, run = 0;
        while (i < n) {
            if (lake[i]) { i++; continue; }
            int ahead = Math.min(n - 1, i + 8);
            double slope = (w[i] - w[ahead]) / (double) Math.max(1, (ahead - i) * STEP);
            long r = mix(hash + 0x632BE59BD9B4E019L * ++run);
            int step = slope > 0.3 ? 2 + (int) (unit(r) * 2) : 1;
            int e = i;
            while (e + 1 < n && !lake[e + 1] && w[i] - w[e + 1] <= step) e++;
            for (int k = i; k < e; k++) w[k] = w[e];
            i = e + 1;
        }
    }
}
