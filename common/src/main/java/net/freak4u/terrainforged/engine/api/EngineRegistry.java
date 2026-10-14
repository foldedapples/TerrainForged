package net.freak4u.terrainforged.engine.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;

/**
 * Process-wide registry so every density function, feature and command that refers to the same world
 * (seed + settings + min Y) shares one engine and its caches. Bounded to a handful of engines (one per open
 * world/dimension, plus a few recently closed ones).
 */
public final class EngineRegistry {
    private record Key(long seed, TerrainSettings settings, int minY) {}

    private static final int CAPACITY = 6;
    private static final Map<Key, TerrainEngine> ENGINES = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, TerrainEngine> eldest) {
            return size() > CAPACITY;
        }
    };

    private EngineRegistry() {}

    public static synchronized TerrainEngine get(long seed, TerrainSettings settings, int minY) {
        Objects.requireNonNull(settings);
        return ENGINES.computeIfAbsent(new Key(seed, settings, minY), k -> new TerrainEngine(seed, settings, minY));
    }

    public static synchronized void clear() {
        ENGINES.clear();
    }
}
