package net.freak4u.terrainforged.engine.preview;

import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import net.freak4u.terrainforged.engine.terrain.RtfTerrain;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;

/**
 * Renders preview maps from a {@link TerrainEngine}: a hill-shaded relief map with hypsometric colours, a climate
 * (biome) map and a contour/height map. Pure Java so the world-creation preview, the developer map tool and tests
 * share it. Made by Freak4U.
 */
public final class ReliefRenderer {
    private ReliefRenderer() {}

    public enum Mode { RELIEF, CLIMATE, HEIGHT }

    /** Data sampled for one map; colours are computed separately. */
    public static final class Map {
        public final int width, height, seaLevel;
        public final float blocksPerPixel;
        public final int originX, originZ; // world position of pixel (0, 0)
        public final ColumnSample[] columns;

        Map(int w, int h, float bpp, int sea, int originX, int originZ, ColumnSample[] columns) {
            this.width = w;
            this.height = h;
            this.blocksPerPixel = bpp;
            this.seaLevel = sea;
            this.originX = originX;
            this.originZ = originZ;
            this.columns = columns;
        }

        /** The column under a pixel (clamped to the map). */
        public ColumnSample at(int px, int pz) {
            px = Math.max(0, Math.min(width - 1, px));
            pz = Math.max(0, Math.min(height - 1, pz));
            return columns[pz * width + px];
        }
    }

    /**
     * RTF's fast preview: raw cells (no droplet erosion) spread {@code bpp} blocks apart - the path ReTerraForged's
     * preset screen uses. {@code tilesPerSide} 128-pixel tiles are stitched into one map (1 = 128 px, 2 = 256 px).
     */
    public static Map preview(TerrainEngine engine, int centerX, int centerZ, float bpp, int tilesPerSide) {
        int n = RtfTerrain.TILE_BLOCKS, size = n * tilesPerSide;
        ColumnSample[] cols = new ColumnSample[size * size];
        float tileSpan = n * bpp;
        float startX = centerX - tilesPerSide * tileSpan / 2.0F, startZ = centerZ - tilesPerSide * tileSpan / 2.0F;
        for (int tz = 0; tz < tilesPerSide; tz++) {
            for (int tx = 0; tx < tilesPerSide; tx++) {
                int cx = Math.round(startX + (tx + 0.5F) * tileSpan), cz = Math.round(startZ + (tz + 0.5F) * tileSpan);
                ColumnSample[] tile = engine.previewTile(cx, cz, bpp);
                for (int z = 0; z < n; z++) System.arraycopy(tile, z * n, cols, (tz * n + z) * size + tx * n, n);
            }
        }
        return new Map(size, size, bpp, engine.settings().seaLevel(), Math.round(startX), Math.round(startZ), cols);
    }

    public static Map preview(TerrainEngine engine, int centerX, int centerZ, float bpp) {
        return preview(engine, centerX, centerZ, bpp, 1);
    }

    /** Full eroded terrain (what the game generates), sampled every {@code bpp} blocks. Null if cancelled. */
    public static Map sample(TerrainEngine engine, int centerX, int centerZ, int bpp, int w, int h, BooleanSupplier cancelled) {
        ColumnSample[] cols = new ColumnSample[w * h];
        IntStream.range(0, h).parallel().forEach(j -> {
            if (cancelled.getAsBoolean()) return;
            for (int i = 0; i < w; i++) {
                int x = centerX + (i - w / 2) * bpp, z = centerZ + (j - h / 2) * bpp;
                cols[j * w + i] = engine.compute(x, z);
            }
        });
        return cancelled.getAsBoolean() ? null : new Map(w, h, bpp, engine.settings().seaLevel(), centerX - w / 2 * bpp, centerZ - h / 2 * bpp, cols);
    }

    /** Largest map scale (blocks per pixel) at which mountain streams are drawn. */
    public static final float STREAM_MAX_BPP = 8;

    /**
     * Marks pixels crossed by mountain streams ({@code RtfTerrain.FLAG_STREAM}). Only streams that start on the map
     * are drawn (cheap enough for the live preview); does nothing above {@link #STREAM_MAX_BPP}.
     */
    public static void overlayStreams(Map m, TerrainEngine engine) {
        if (m.blocksPerPixel > STREAM_MAX_BPP || !engine.streams().enabled()) return;
        int maxX = Math.round(m.originX + m.width * m.blocksPerPixel), maxZ = Math.round(m.originZ + m.height * m.blocksPerPixel);
        for (var p : engine.streams().pathsStartingIn(m.originX, m.originZ, maxX, maxZ)) {
            for (int i = 0; i < p.length; i++) mark(m, p.x[i], p.z[i]);
            for (int i = 0; i < p.lakeX.length; i++) mark(m, p.lakeX[i], p.lakeZ[i]);
        }
    }

    private static void mark(Map m, int x, int z) {
        int px = (int) Math.floor((x - m.originX) / m.blocksPerPixel), pz = (int) Math.floor((z - m.originZ) / m.blocksPerPixel);
        if (px < 0 || pz < 0 || px >= m.width || pz >= m.height) return;
        ColumnSample c = m.columns[pz * m.width + px];
        if (c.height >= m.seaLevel) c.flags |= RtfTerrain.FLAG_STREAM;
    }

    public static int[] colour(Map m) {
        return colour(m, Mode.RELIEF);
    }

    /** ARGB pixels (alpha 0xFF). */
    public static int[] colour(Map m, Mode mode) {
        int w = m.width, h = m.height, sea = m.seaLevel;
        int[] out = new int[w * h];
        // light from the north-west, slightly above the horizon
        double lx = -0.55, lz = -0.55, ly = 0.63;
        double exaggeration = 1.6 / Math.max(1.0, m.blocksPerPixel);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                ColumnSample c = m.columns[j * w + i];
                double ht = c.height;
                double dx = (m.at(i + 1, j).height - m.at(i - 1, j).height) * 0.5 * exaggeration;
                double dz = (m.at(i, j + 1).height - m.at(i, j - 1).height) * 0.5 * exaggeration;
                double nx = -dx, nz = -dz, ny = 1.0, len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double light = Math.max(0, (nx * lx + ny * ly + nz * lz) / len);
                double shade = 0.45 + 0.75 * light;
                double[] rgb = switch (mode) {
                    case RELIEF -> relief(m, c, ht, sea);
                    case CLIMATE -> climate(c, ht, sea);
                    case HEIGHT -> c.is(RtfTerrain.FLAG_STREAM) && ht >= sea ? STREAM
                        : height(ht, sea, contour(ht, m.at(i + 1, j).height) || contour(ht, m.at(i, j + 1).height));
                };
                boolean water = ht < sea;
                double s = water ? 0.85 + 0.15 * shade : shade;
                if (mode == Mode.HEIGHT) s = 1;
                out[j * w + i] = 0xFF000000 | (c8(rgb[0] * s) << 16) | (c8(rgb[1] * s) << 8) | c8(rgb[2] * s);
            }
        }
        return out;
    }

    private static boolean inlandWater(ColumnSample c) {
        return c.is(RtfTerrain.FLAG_RIVER) || c.is(RtfTerrain.FLAG_LAKE) || c.is(RtfTerrain.FLAG_WETLAND);
    }

    private static double[] water(ColumnSample c, double ht, int sea) {
        if (inlandWater(c)) return new double[]{58, 128, 218};
        double d = Math.min(1, (sea - ht) / 55.0);
        return lerp(new double[]{70, 150, 200}, new double[]{18, 44, 110}, Math.sqrt(d));
    }

    private static final double[] STREAM = {64, 140, 226};

    private static double[] relief(Map m, ColumnSample c, double ht, int sea) {
        if (ht < sea) return water(c, ht, sea);
        if (c.is(RtfTerrain.FLAG_STREAM)) return STREAM;
        double e = (ht - sea) / 170.0;
        boolean cold = c.vTemperature < -0.45;
        if (cold && (e > 0.3 || c.vTemperature < -0.6)) return new double[]{238, 240, 246};
        if ((m.blocksPerPixel <= 2 && c.gradient > 0.6) || e > 0.78) return new double[]{132, 128, 124};
        if (c.is(RtfTerrain.FLAG_BEACH) && ht < sea + 4) return new double[]{220, 208, 160};
        if (c.is(RtfTerrain.FLAG_WETLAND)) return new double[]{78, 112, 74};
        if (c.vHumidity < -0.35 && c.vTemperature > 0.55) return lerp(new double[]{216, 190, 132}, new double[]{176, 140, 96}, e);
        // hypsometric tint: lush lowland -> upland green -> brown highland -> grey
        if (e < 0.25) return lerp(new double[]{92, 150, 72}, new double[]{122, 160, 82}, e / 0.25);
        if (e < 0.5) return lerp(new double[]{122, 160, 82}, new double[]{150, 140, 92}, (e - 0.25) / 0.25);
        return lerp(new double[]{150, 140, 92}, new double[]{140, 132, 124}, (e - 0.5) / 0.28);
    }

    /** Colours by vanilla temperature/humidity (roughly the biome you will find there). */
    private static double[] climate(ColumnSample c, double ht, int sea) {
        if (ht < sea) {
            double[] w = water(c, ht, sea);
            if (c.vTemperature < -0.45 && !inlandWater(c)) return lerp(w, new double[]{180, 205, 230}, 0.45);
            return w;
        }
        if (c.is(RtfTerrain.FLAG_STREAM)) return STREAM;
        double t = (c.vTemperature + 1) / 2, h = (c.vHumidity + 1) / 2; // 0..1
        double[] cold = lerp(new double[]{200, 210, 220}, new double[]{232, 240, 250}, h);
        double[] temperate = lerp(new double[]{170, 190, 100}, new double[]{40, 120, 60}, h);
        double[] hot = lerp(new double[]{226, 200, 130}, new double[]{30, 110, 40}, h);
        if (t < 0.3) return lerp(cold, temperate, t / 0.3);
        if (t < 0.65) return temperate;
        return lerp(temperate, hot, Math.min(1, (t - 0.65) / 0.3));
    }

    private static boolean contour(double a, double b) {
        return Math.floorDiv((int) Math.floor(a), 16) != Math.floorDiv((int) Math.floor(b), 16);
    }

    /** Grey levels by height with a contour line every 16 blocks. */
    private static double[] height(double ht, int sea, boolean contour) {
        double v = Math.max(0, Math.min(1, (ht - 20) / 220.0));
        double g = 40 + 200 * v;
        if (ht < sea) return contour ? new double[]{g * 0.4, g * 0.5, g * 0.8} : new double[]{g * 0.55, g * 0.65, g * 0.95};
        if (contour) return new double[]{g * 0.55, g * 0.42, g * 0.3};
        return new double[]{g, g, g * 0.95};
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static int c8(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}
