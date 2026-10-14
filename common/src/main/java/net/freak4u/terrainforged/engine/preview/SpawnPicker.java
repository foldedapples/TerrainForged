package net.freak4u.terrainforged.engine.preview;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

import net.freak4u.terrainforged.engine.terrain.RtfTerrain;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;

/**
 * Picks a pleasant spawn point on a preview map (the Customize screen's "Random" button): dry land 3-45 blocks above
 * the sea, fairly flat (no mountains or cliffs), 4-96 blocks from a river, lake or mountain stream, preferring
 * temperate climates, away from the map edge. Weighted random among all such spots, so asking again gives another.
 * Made by Freak4U.
 */
public final class SpawnPicker {
    public static final double MAX_RIVER_DISTANCE = 96, MAX_SLOPE = 0.25;
    public static final int MIN_ABOVE_SEA = 3, MAX_ABOVE_SEA = 45;

    private SpawnPicker() {}

    /** World {x, z} of the chosen spot, or null if the map has none. */
    public static int[] pick(ReliefRenderer.Map map, Random random) {
        int w = map.width, h = map.height, sea = map.seaLevel;
        float bpp = map.blocksPerPixel;
        int[] dist = waterDistance(map);
        double total = 0;
        double[] weight = new double[w * h];
        int margin = 6;
        for (int z = margin; z < h - margin; z++) {
            for (int x = margin; x < w - margin; x++) {
                int k = z * w + x;
                double score = score(map, x, z, dist[k], bpp, sea);
                weight[k] = score * score;
                total += weight[k];
            }
        }
        if (total <= 0) return null;
        double r = random.nextDouble() * total;
        int pick = -1;
        for (int k = 0; k < weight.length && r >= 0; k++) {
            if (weight[k] <= 0) continue;
            r -= weight[k];
            pick = k;
        }
        int x = pick % w, z = pick / w;
        return new int[]{Math.round(map.originX + (x + 0.5F) * bpp), Math.round(map.originZ + (z + 0.5F) * bpp)};
    }

    /** 0 if the pixel is not a candidate, else higher is better (flatter, closer to water, temperate). */
    static double score(ReliefRenderer.Map map, int x, int z, int waterPixels, float bpp, int sea) {
        ColumnSample c = map.at(x, z);
        if (c.flags != 0) return 0; // water, stream, beach, volcano...
        double above = c.height - sea;
        if (above < MIN_ABOVE_SEA || above > MAX_ABOVE_SEA) return 0;
        double slope = slope(map, x, z, bpp);
        if (slope > MAX_SLOPE) return 0;
        double river = waterPixels == Integer.MAX_VALUE ? Double.MAX_VALUE : waterPixels * bpp;
        if (river < 4 || river > MAX_RIVER_DISTANCE) return 0;
        double climate = c.vTemperature < -0.45 || c.vTemperature > 0.55 ? 0.35 : 1.0;
        return Math.max(0.02, (1 - slope / MAX_SLOPE)) * Math.max(0.02, 1 - river / MAX_RIVER_DISTANCE) * climate;
    }

    /** Steepest height change per block around a pixel. */
    public static double slope(ReliefRenderer.Map map, int x, int z, float bpp) {
        return Math.max(Math.abs(map.at(x + 1, z).height - map.at(x - 1, z).height),
            Math.abs(map.at(x, z + 1).height - map.at(x, z - 1).height)) / (2.0 * bpp);
    }

    /** Pixel distance (4-neighbour steps) to the nearest river, lake or stream pixel; MAX_VALUE if none. */
    public static int[] waterDistance(ReliefRenderer.Map map) {
        int w = map.width, h = map.height;
        int[] dist = new int[w * h];
        Arrays.fill(dist, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int k = 0; k < dist.length; k++) {
            ColumnSample c = map.columns[k];
            if (c.is(RtfTerrain.FLAG_RIVER) || c.is(RtfTerrain.FLAG_LAKE) || (c.is(RtfTerrain.FLAG_STREAM) && c.height >= map.seaLevel)) {
                dist[k] = 0;
                queue.add(k);
            }
        }
        while (!queue.isEmpty()) {
            int k = queue.poll(), x = k % w, z = k / w, d = dist[k] + 1;
            if (x > 0 && dist[k - 1] > d) { dist[k - 1] = d; queue.add(k - 1); }
            if (x < w - 1 && dist[k + 1] > d) { dist[k + 1] = d; queue.add(k + 1); }
            if (z > 0 && dist[k - w] > d) { dist[k - w] = d; queue.add(k - w); }
            if (z < h - 1 && dist[k + w] > d) { dist[k + w] = d; queue.add(k + w); }
        }
        return dist;
    }
}
