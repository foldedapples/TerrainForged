package net.freak4u.terrainforged.engine;

import java.util.Arrays;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;

/** Developer tool: terrain statistics over an area (raw RTF cells). Usage: Stats seed halfSizeBlocks step */
public final class Stats {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        int half = Integer.parseInt(a[1]), step = Integer.parseInt(a[2]);
        TerrainEngine e = new TerrainEngine(seed, TerrainSettings.defaults());
        int sea = e.settings().seaLevel();
        int n = 0, land = 0, river = 0, lake = 0, wetland = 0;
        double[] hs = new double[(2 * half / step + 1) * (2 * half / step + 1)];
        int k = 0;
        for (int z = -half; z <= half; z += step) {
            for (int x = -half; x <= half; x += step) {
                ColumnSample c = e.preview(x, z);
                n++;
                if (c.height >= sea) { land++; hs[k++] = c.height; }
                if (c.is(RtfTerrain.FLAG_RIVER)) river++;
                if (c.is(RtfTerrain.FLAG_LAKE)) lake++;
                if (c.is(RtfTerrain.FLAG_WETLAND)) wetland++;
            }
        }
        double[] lh = Arrays.copyOf(hs, k);
        Arrays.sort(lh);
        System.out.printf("land %.1f%%  river %.2f%%  lake %.2f%%  wetland %.2f%%%n", 100.0 * land / n, 100.0 * river / n, 100.0 * lake / n, 100.0 * wetland / n);
        if (k > 0) System.out.printf("land height p10 %.0f p50 %.0f p90 %.0f p99 %.0f max %.0f%n", lh[k / 10], lh[k / 2], lh[k * 9 / 10], lh[k * 99 / 100], lh[k - 1]);
    }
}
