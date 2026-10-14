package net.freak4u.terrainforged.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.freak4u.terrainforged.mc.SpawnPlacer;
import net.freak4u.terrainforged.mc.TerrainForgedMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;

/** Fabric entry point: registration only - all worldgen logic is shared with the Forge build. Made by Freak4U. */
public final class TerrainForgedFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TerrainForgedMod.init(() -> FabricLoader.getInstance().getConfigDir());
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, TerrainForgedMod.id(TerrainForgedMod.DENSITY_FUNCTION_ID), TerrainForgedMod.DENSITY_FUNCTION_CODEC);
        Registry.register(BuiltInRegistries.FEATURE, TerrainForgedMod.id(TerrainForgedMod.ERODE_ID), TerrainForgedMod.ERODE);
        Registry.register(BuiltInRegistries.FEATURE, TerrainForgedMod.id(TerrainForgedMod.DECORATE_SNOW_ID), TerrainForgedMod.DECORATE_SNOW);
        Registry.register(BuiltInRegistries.FEATURE, TerrainForgedMod.id(TerrainForgedMod.STREAMS_ID), TerrainForgedMod.STREAMS);
        // all three features are no-ops in worlds that do not use TerrainForged, so adding them to all overworld biomes is safe
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.RAW_GENERATION,
            ResourceKey.create(Registries.PLACED_FEATURE, TerrainForgedMod.id(TerrainForgedMod.ERODE_ID)));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
            ResourceKey.create(Registries.PLACED_FEATURE, TerrainForgedMod.id(TerrainForgedMod.DECORATE_SNOW_ID)));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LAKES,
            ResourceKey.create(Registries.PLACED_FEATURE, TerrainForgedMod.id(TerrainForgedMod.STREAMS_ID)));
        // spawn point picked on the Customize screen's map (applied once per world)
        ServerLifecycleEvents.SERVER_STARTED.register(SpawnPlacer::onServerStarted);
    }
}
