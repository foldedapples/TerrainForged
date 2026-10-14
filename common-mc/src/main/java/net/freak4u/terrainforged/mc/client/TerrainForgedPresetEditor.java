package net.freak4u.terrainforged.mc.client;

import java.util.Map;

import net.freak4u.terrainforged.mc.TerrainForgedConfig;
import net.freak4u.terrainforged.mc.TerrainForgedMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * "Customize" button handler for the TerrainForged world presets (client only). Fabric hooks it in through a
 * small mixin on {@code WorldCreationUiState#getPresetEditor}; Forge through {@code RegisterPresetEditorsEvent}.
 */
public final class TerrainForgedPresetEditor implements PresetEditor {
    public static final ResourceKey<WorldPreset> STANDARD = ResourceKey.create(Registries.WORLD_PRESET, TerrainForgedMod.id("terrainforged"));
    public static final ResourceKey<WorldPreset> TALL = ResourceKey.create(Registries.WORLD_PRESET, TerrainForgedMod.id("terrainforged_tall"));

    private final TerrainForgedConfig.Profile profile;

    public TerrainForgedPresetEditor(TerrainForgedConfig.Profile profile) {
        this.profile = profile;
    }

    /** Editors keyed by preset, for loaders with a registration hook. */
    public static Map<ResourceKey<WorldPreset>, PresetEditor> editors() {
        return Map.of(STANDARD, new TerrainForgedPresetEditor(TerrainForgedConfig.Profile.STANDARD),
            TALL, new TerrainForgedPresetEditor(TerrainForgedConfig.Profile.TALL));
    }

    /** Editor for a preset key, or null when it is not a TerrainForged preset. */
    public static PresetEditor forPreset(ResourceKey<WorldPreset> key) {
        if (STANDARD.equals(key)) return new TerrainForgedPresetEditor(TerrainForgedConfig.Profile.STANDARD);
        if (TALL.equals(key)) return new TerrainForgedPresetEditor(TerrainForgedConfig.Profile.TALL);
        return null;
    }

    @Override
    public Screen createEditScreen(CreateWorldScreen parent, WorldCreationContext context) {
        return new TerrainForgedSettingsScreen(parent, context, profile);
    }
}
