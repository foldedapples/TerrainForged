package net.freak4u.terrainforged.mc;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Loader-neutral constants and objects. Each loader registers {@link #DENSITY_FUNCTION_CODEC},
 * {@link #ERODE}, {@link #DECORATE_SNOW} and {@link #STREAMS} under the ids below using its own registration mechanism.
 *
 * Made by Freak4U.
 */
public final class TerrainForgedMod {
    public static final String MOD_ID = "terrainforged";
    public static final String MOD_NAME = "TerrainForged";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static final String DENSITY_FUNCTION_ID = "engine";
    public static final String ERODE_ID = "erode";
    public static final String DECORATE_SNOW_ID = "decorate_snow";
    public static final String STREAMS_ID = "streams";

    public static final MapCodec<EngineDensityFunction> DENSITY_FUNCTION_CODEC = EngineDensityFunction.MAP_CODEC;
    public static final Feature<NoneFeatureConfiguration> ERODE = new ErodeFeature();
    public static final Feature<NoneFeatureConfiguration> DECORATE_SNOW = new DecorateSnowFeature();
    public static final Feature<NoneFeatureConfiguration> STREAMS = new StreamFeature();

    private static Supplier<Path> configDir = () -> Paths.get("config");

    private TerrainForgedMod() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Called by the loader entry point before any world is created. */
    public static void init(Supplier<Path> loaderConfigDir) {
        configDir = loaderConfigDir;
        TerrainForgedConfig.ensureDefaults();
        LOGGER.info("{} initialised (Made by Freak4U); config folder: {}", MOD_NAME, configDir.get());
    }

    public static Path configDir() {
        return configDir.get();
    }
}
