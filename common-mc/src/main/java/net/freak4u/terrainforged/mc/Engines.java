package net.freak4u.terrainforged.mc;

import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;

/** Finds the TerrainForged engine a level (or random state) generates with; null for non-TerrainForged worlds. */
public final class Engines {
    private Engines() {}

    public static TerrainEngine of(ServerLevel level) {
        return of(level.getChunkSource().randomState());
    }

    public static TerrainEngine of(RandomState rs) {
        EngineDensityFunction edf = EngineDensityFunction.unwrap(rs.router().preliminarySurfaceLevel());
        return edf == null ? null : edf.engine();
    }
}
