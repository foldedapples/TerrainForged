package net.freak4u.terrainforged.mc;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.freak4u.terrainforged.engine.config.TerrainSettings;

/**
 * Serialises {@link TerrainSettings} as a flat {@code {"terrain.mountain_height": 200.0, ...}} object.
 *
 * <p>World-creation hook without mixins: the bundled world presets contain {@code "from_config": 1} (or 2 for the
 * tall preset). Decoding such a map reads the config file once (a saved map without stream settings comes from a
 * world made before 0.6.0 and decodes with streams off); encoding always writes the concrete values, so
 * as soon as the world is saved its level.dat holds a frozen copy of the settings and later config edits cannot
 * alter terrain in an existing save.
 */
public final class SettingsCodec {
    public static final String FROM_CONFIG = "from_config";

    public static final Codec<TerrainSettings> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).comapFlatMap(
        SettingsCodec::decode, s -> new HashMap<>(s.toMap()));

    private SettingsCodec() {}

    private static DataResult<TerrainSettings> decode(Map<String, Double> map) {
        Double marker = map.get(FROM_CONFIG);
        if (marker != null) {
            TerrainForgedConfig.Profile p = marker >= 2 ? TerrainForgedConfig.Profile.TALL : TerrainForgedConfig.Profile.STANDARD;
            return DataResult.success(TerrainForgedConfig.load(p));
        }
        // worlds created before 0.6.0 have no stream settings: keep them stream-free so new chunks match old ones
        if (!map.containsKey(TerrainSettings.Key.STREAM_CHANCE.id)) {
            map = new HashMap<>(map);
            map.put(TerrainSettings.Key.STREAM_CHANCE.id, 0.0);
        }
        return DataResult.success(TerrainSettings.fromMap(map));
    }
}
