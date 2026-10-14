package net.freak4u.terrainforged.engine.worldgen;

/**
 * Decides, for one area of real terrain, where stream water goes. Pure (no Minecraft types) so it can be tested
 * against simulated terrain; the Minecraft feature only reads heights and writes the blocks this plan describes.
 *
 * <p>Rules for a stream column at (x, z) with planned level {@code L} (from {@link StreamNetwork}):
 * <ol>
 *   <li>skip it if water already lies on top (rivers, lakes, the sea);</li>
 *   <li>the water surface starts at {@code L} (level with the ground for streams, the lake surface for lakes);</li>
 *   <li>containment: the surface is lowered to the surface height of every horizontal neighbour that will not
 *       hold stream water itself, so the water is walled in by terrain (or meets other water) on all four
 *       sides - it can only flow along the stream, never spill onto the land - and channels are full to the brim
 *       of their lower bank;</li>
 *   <li>if that would cut more than {@link StreamNetwork#MAX_CUT} blocks into the ground the column stays dry
 *       (rule 3 then treats it as a wall for its neighbours).</li>
 * </ol>
 * Neighbouring stream columns may sit lower; water then pours into them - rapids and waterfalls. The decision for
 * a column depends only on terrain within two blocks of it, so neighbouring chunks agree on shared edges.
 * Made by Freak4U.
 */
public final class StreamCarver {
    public static final int NONE = StreamNetwork.NONE;
    private static final int HALO = 2;

    /** Terrain heights (block y) as the world currently has them. */
    public interface Terrain {
        /** Highest solid (motion-blocking, not fluid) block. */
        int solidTop(int x, int z);

        /** Highest non-air block, fluids included. */
        int surfaceTop(int x, int z);
    }

    /** Result for the area [x0, x0+w) x [z0, z0+h). */
    public static final class Plan {
        public final int x0, z0, w, h;
        /** top water block y, or NONE */
        public final int[] water;
        /** bed block y (below the water; the existing ground where the water is deeper than the stream's depth) */
        public final int[] bed;
        /** ground top before carving (everything from water+1 up to this becomes air) */
        public final int[] top;
        private final int[] levels; // network levels over the area plus HALO, for edge queries

        Plan(int x0, int z0, int w, int h, int[] levels) {
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            this.water = new int[w * h];
            this.bed = new int[w * h];
            this.top = new int[w * h];
            this.levels = levels;
            java.util.Arrays.fill(water, NONE);
        }

        public boolean any() {
            for (int v : water) if (v != NONE) return true;
            return false;
        }

        /** True if a stream passes the column (also for columns up to two blocks outside the area). */
        public boolean isStream(int x, int z) {
            int W = w + 2 * HALO, lx = x - x0 + HALO, lz = z - z0 + HALO;
            if (lx < 0 || lz < 0 || lx >= W || lz >= h + 2 * HALO) return false;
            return levels[lz * W + lx] != NONE;
        }
    }

    private StreamCarver() {}

    public static Plan plan(StreamNetwork network, int x0, int z0, int w, int h, Terrain terrain) {
        int W = w + 2 * HALO, H = h + 2 * HALO, ox = x0 - HALO, oz = z0 - HALO;
        int[] depth = new int[W * H];
        int[] lv = network.waterLevels(ox, oz, W, H, depth);
        Plan plan = new Plan(x0, z0, w, h, lv);
        boolean any = false;
        for (int v : lv) if (v != NONE) { any = true; break; }
        if (!any) return plan;

        // pass 1: rules 1-4 for the area plus a 1-block ring (the ring's result is needed by rule 3 in pass 2)
        int[] solid = new int[W * H], surface = new int[W * H];
        for (int k = 0; k < solid.length; k++) solid[k] = surface[k] = NONE;
        int[] wet = new int[W * H];
        java.util.Arrays.fill(wet, NONE);
        for (int lz = 1; lz < H - 1; lz++) {
            for (int lx = 1; lx < W - 1; lx++) {
                int k = lz * W + lx;
                if (lv[k] == NONE) continue;
                wet[k] = level(lv, k, W, ox, oz, lx, lz, terrain, solid, surface, null);
            }
        }
        // pass 2: also treat stream columns that stayed dry as walls
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int lx = x + HALO, lz = z + HALO, k = lz * W + lx;
                if (wet[k] == NONE) continue;
                int wl = level(lv, k, W, ox, oz, lx, lz, terrain, solid, surface, wet);
                if (wl == NONE) continue;
                int i = z * w + x;
                plan.water[i] = wl;
                int top = height(solid, k, ox + lx, oz + lz, terrain, true);
                plan.bed[i] = Math.min(wl - Math.max(1, depth[k]), top);
                plan.top[i] = top;
            }
        }
        return plan;
    }

    private static int level(int[] lv, int k, int W, int ox, int oz, int lx, int lz, Terrain t, int[] solid, int[] surface, int[] wet) {
        int x = ox + lx, z = oz + lz;
        int top = height(solid, k, x, z, t, true);
        if (height(surface, k, x, z, t, false) > top) return NONE; // water already here
        int wl = lv[k];
        int[] nk = {k - 1, k + 1, k - W, k + W};
        int[][] nxz = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
        for (int d = 0; d < 4; d++) {
            int n = nk[d];
            boolean wall = lv[n] == NONE || (wet != null && wet[n] == NONE);
            if (wall) wl = Math.min(wl, height(surface, n, nxz[d][0], nxz[d][1], t, false));
        }
        if (wet == null && top - wl > StreamNetwork.MAX_CUT) return NONE;
        return wl;
    }

    private static int height(int[] cache, int k, int x, int z, Terrain t, boolean solid) {
        int v = cache[k];
        if (v == NONE) v = cache[k] = solid ? t.solidTop(x, z) : t.surfaceTop(x, z);
        return v;
    }
}
