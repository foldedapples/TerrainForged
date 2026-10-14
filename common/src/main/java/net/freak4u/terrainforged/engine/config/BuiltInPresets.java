package net.freak4u.terrainforged.engine.config;

import java.util.LinkedHashMap;
import java.util.Map;

import net.freak4u.terrainforged.engine.config.TerrainSettings.Key;
import net.freak4u.terrainforged.engine.rtf.settings.Presets;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;

/**
 * Presets offered on the Customize screen: TerrainForged's own default first (taller mountains, more hills, rivers,
 * lakes and mountain streams with waterfalls), then ReTerraForged 1.20.1's presets ({@code Presets.makeLegacy*},
 * converted with {@link RtfTerrain#fromPreset}; they get TerrainForged's streams too), then two variations of the
 * default. Made by Freak4U.
 */
public final class BuiltInPresets {
    /** Name of the preset equal to {@link TerrainSettings#defaults()}. */
    public static final String DEFAULT = "TerrainForged";

    private BuiltInPresets() {}

    public static Map<String, TerrainSettings> all() {
        Map<String, TerrainSettings> out = new LinkedHashMap<>();
        out.put(DEFAULT, TerrainSettings.defaults());
        out.put("ReTerraForged", RtfTerrain.fromPreset(Presets.makeLegacyDefault()));
        out.put("Beautiful", RtfTerrain.fromPreset(Presets.makeLegacyBeautiful()));
        out.put("Huge Biomes", RtfTerrain.fromPreset(Presets.makeLegacyHugeBiomes()));
        out.put("Lite", RtfTerrain.fromPreset(Presets.makeLegacyLite()));
        out.put("Vanilla-ish", RtfTerrain.fromPreset(Presets.makeLegacyVanillaish()));
        TerrainSettings d = TerrainSettings.defaults();
        out.put("Archipelago", d.with(Key.CONTINENT_SCALE, 1400).with(Key.CONTINENT_SKIPPING, 0.55).with(Key.CONTINENT_SIZE_VARIANCE, 0.6)
            .with(Key.RIVER_COUNT, 5));
        out.put("Alpine", d.with(Key.MOUNTAINS_WEIGHT, 6.0).with(Key.TORRIDONIAN_WEIGHT, 3.0).with(Key.HILLS_WEIGHT, 3.0)
            .with(Key.PLAINS_WEIGHT, 1.0).with(Key.STEPPE_WEIGHT, 0.5).with(Key.REGION_SIZE, 1000).with(Key.TEMPERATURE_BIAS, -0.15));
        return out;
    }
}
