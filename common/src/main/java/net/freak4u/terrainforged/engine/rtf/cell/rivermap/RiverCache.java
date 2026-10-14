// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// TerrainForged changes: RTF's expiring concurrent cache replaced by a bounded LRU with per-key futures.
package net.freak4u.terrainforged.engine.rtf.cell.rivermap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongFunction;

import net.freak4u.terrainforged.engine.rtf.util.PosUtil;

public class RiverCache {
    private static final int CAPACITY = 64;
    protected RiverGenerator generator;
    private final LinkedHashMap<Long, CompletableFuture<Rivermap>> cache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<Rivermap>> eldest) {
            return this.size() > CAPACITY;
        }
    };

    public RiverCache(RiverGenerator generator) {
        this.generator = generator;
    }

    public Rivermap getRivers(int x, int z) {
        return this.computeIfAbsent(PosUtil.pack(x, z), id -> this.generator.generateRivers(PosUtil.unpackLeft(id), PosUtil.unpackRight(id), id));
    }

    protected Rivermap computeIfAbsent(long key, LongFunction<Rivermap> factory) {
        CompletableFuture<Rivermap> f;
        boolean owner = false;
        synchronized (this.cache) {
            f = this.cache.get(key);
            if (f == null) {
                f = new CompletableFuture<>();
                this.cache.put(key, f);
                owner = true;
            }
        }
        if (owner) {
            try {
                f.complete(factory.apply(key));
            } catch (Throwable t) {
                synchronized (this.cache) {
                    this.cache.remove(key);
                }
                f.completeExceptionally(t);
            }
        }
        return f.join();
    }
}
