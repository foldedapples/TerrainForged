package net.freak4u.terrainforged.forge;

import com.mojang.serialization.MapCodec;

import net.freak4u.terrainforged.mc.SpawnPlacer;
import net.freak4u.terrainforged.mc.TerrainForgedMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Forge entry point: registration only - all worldgen logic is shared with the Fabric build. The erosion, snow
 * and stream decorators are added to overworld biomes by data/terrainforged/forge/biome_modifier/*.json.
 * Made by Freak4U.
 */
@Mod(TerrainForgedMod.MOD_ID)
public final class TerrainForgedForge {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
        DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, TerrainForgedMod.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, TerrainForgedMod.MOD_ID);

    static {
        DENSITY_FUNCTIONS.register(TerrainForgedMod.DENSITY_FUNCTION_ID, () -> TerrainForgedMod.DENSITY_FUNCTION_CODEC);
        FEATURES.register(TerrainForgedMod.ERODE_ID, () -> TerrainForgedMod.ERODE);
        FEATURES.register(TerrainForgedMod.DECORATE_SNOW_ID, () -> TerrainForgedMod.DECORATE_SNOW);
        FEATURES.register(TerrainForgedMod.STREAMS_ID, () -> TerrainForgedMod.STREAMS);
    }

    public TerrainForgedForge(FMLJavaModLoadingContext context) {
        TerrainForgedMod.init(FMLPaths.CONFIGDIR::get);
        var modBus = context.getModBusGroup();
        DENSITY_FUNCTIONS.register(modBus);
        FEATURES.register(modBus);
        // spawn point picked on the Customize screen's map (applied once per world)
        ServerStartedEvent.BUS.addListener(event -> SpawnPlacer.onServerStarted(event.getServer()));
        if (FMLEnvironment.dist == Dist.CLIENT) TerrainForgedForgeClient.register(modBus); // "Customize" screen
    }
}
