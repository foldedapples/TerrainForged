package net.freak4u.terrainforged.mc;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * Reads and replaces the TerrainForged settings embedded in a (not yet created) world's overworld generator.
 * Replacing builds a new {@link NoiseGeneratorSettings} whose router carries copies of every
 * {@code terrainforged:engine} function bound to the new settings; everything else is kept as-is. Because the
 * settings are part of the generator, they are written to level.dat and stay with the world.
 */
public final class GeneratorSettingsUtil {
    private GeneratorSettingsUtil() {}

    /** First TerrainForged engine function found in the generator's router, or null. */
    public static EngineDensityFunction findEngineFunction(ChunkGenerator generator) {
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) return null;
        EngineDensityFunction[] found = new EngineDensityFunction[1];
        noise.generatorSettings().value().noiseRouter().mapAll(f -> {
            if (found[0] == null && f instanceof EngineDensityFunction e) found[0] = e;
            return f;
        });
        return found[0];
    }

    /** Returns a generator identical to {@code generator} but using {@code settings}, or null if not TerrainForged. */
    public static ChunkGenerator withSettings(ChunkGenerator generator, TerrainSettings settings) {
        EngineDensityFunction current = findEngineFunction(generator);
        if (!(generator instanceof NoiseBasedChunkGenerator noise) || current == null) return null;
        int seaLevel = settings.fitToWorld(current.minY(), current.maxY()).seaLevel();
        NoiseGeneratorSettings old = noise.generatorSettings().value();
        DensityFunction.Visitor swap = f -> f instanceof EngineDensityFunction e ? e.withSettings(settings) : f;
        NoiseRouter router = old.noiseRouter().mapAll(swap);
        NoiseGeneratorSettings updated = new NoiseGeneratorSettings(old.noiseSettings(), old.defaultBlock(), old.defaultFluid(),
            router, old.surfaceRule(), old.spawnTarget(), seaLevel, old.disableMobGeneration(), old.aquifersEnabled(),
            old.oreVeinsEnabled(), old.useLegacyRandomSource());
        return new NoiseBasedChunkGenerator(noise.getBiomeSource(), Holder.direct(updated));
    }
}
