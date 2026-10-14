package net.freak4u.terrainforged.mc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Moves the world spawn to the point picked on the Customize screen's preview map.
 *
 * <p>Runs once, when the server has started (both loaders call {@link #onServerStarted}): vanilla has already chosen
 * its own spawn by then and no player has joined yet. A marker file in the world folder records that the spawn was
 * placed, so later restarts (and a later {@code /setworldspawn}) are left alone. If the picked point is in water,
 * the nearest dry land within {@link #SEARCH_RADIUS} blocks is used. Made by Freak4U.
 */
public final class SpawnPlacer {
    static final String MARKER = "terrainforged_spawn_placed.txt";
    static final int SEARCH_RADIUS = 128;
    private static final int SEARCH_STEP = 8;

    private SpawnPlacer() {}

    public static void onServerStarted(MinecraftServer server) {
        try {
            place(server);
        } catch (RuntimeException ex) {
            TerrainForgedMod.LOGGER.warn("Could not place the chosen spawn point; keeping the vanilla spawn", ex);
        }
    }

    private static void place(MinecraftServer server) {
        ServerLevel level = server.overworld();
        EngineDensityFunction f = GeneratorSettingsUtil.findEngineFunction(level.getChunkSource().getGenerator());
        if (f == null || !f.settings().hasSpawn()) return;
        Path marker = server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
        if (Files.exists(marker)) return;
        TerrainEngine engine = Engines.of(level);
        TerrainSettings s = f.settings();
        int[] xz = engine != null ? findDryLand(engine, s.spawnX(), s.spawnZ(), s.seaLevel()) : new int[]{s.spawnX(), s.spawnZ()};
        int x = xz[0], z = xz[1];
        level.getChunk(x >> 4, z >> 4); // generate the spawn chunk so the heightmap is final
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        server.setRespawnData(LevelData.RespawnData.of(level.dimension(), new BlockPos(x, y, z), 0.0F, 0.0F));
        TerrainForgedMod.LOGGER.info("World spawn set to the chosen point {}, {}, {} (picked {}, {})", x, y, z, s.spawnX(), s.spawnZ());
        try {
            Files.writeString(marker, "TerrainForged placed the world spawn at " + x + " " + y + " " + z
                + ". Delete this file to place it again on the next start.\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            TerrainForgedMod.LOGGER.warn("Could not write {}: {}", marker, ex.toString());
        }
    }

    /** The picked point if it is dry land, else the closest dry column on a ring search; the picked point if none. */
    static int[] findDryLand(TerrainEngine engine, int x, int z, int seaLevel) {
        if (isDry(engine.sample(x, z), seaLevel)) return new int[]{x, z};
        for (int r = SEARCH_STEP; r <= SEARCH_RADIUS; r += SEARCH_STEP) {
            int bestX = 0, bestZ = 0;
            long bestD = Long.MAX_VALUE;
            for (int dx = -r; dx <= r; dx += SEARCH_STEP) {
                for (int dz = -r; dz <= r; dz += SEARCH_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // ring only
                    long d = (long) dx * dx + (long) dz * dz;
                    if (d < bestD && isDry(engine.sample(x + dx, z + dz), seaLevel)) {
                        bestD = d;
                        bestX = x + dx;
                        bestZ = z + dz;
                    }
                }
            }
            if (bestD != Long.MAX_VALUE) return new int[]{bestX, bestZ};
        }
        return new int[]{x, z};
    }

    static boolean isDry(ColumnSample c, int seaLevel) {
        return c.height >= seaLevel + 1 && !c.is(RtfTerrain.FLAG_RIVER) && !c.is(RtfTerrain.FLAG_LAKE)
            && !c.is(RtfTerrain.FLAG_OCEAN) && !c.is(RtfTerrain.FLAG_WETLAND);
    }
}
