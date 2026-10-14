// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.tile;

public record Size(int size, int mask, int border, int total, int lowerBorder, int upperBorder, int arraySize) {
        
    public int mask(int i) {
        return i & this.mask;
    }
    
    public int indexOf(int x, int z) {
        return z * this.total + x;
    }
    
    public static int chunkToBlock(int i) {
        return i << 4;
    }
    
    public static int blockToChunk(int i) {
        return i >> 4;
    }
    
    public static int count(int minX, int minZ, int maxX, int maxZ) {
        int dx = maxX - minX;
        int dz = maxZ - minZ;
        return dx * dz;
    }
    
    public static Size make(int size, int border) {
    	int total = size + 2 * border;
    	return new Size(size, size - 1, border, total, border, border + size, total * total);
    }
    
    public static Size chunks(int factor, int borderChunks) {
        int chunks = 1 << factor;
        return make(chunks, borderChunks);
    }
    
    public static Size blocks(int factor, int borderChunks) {
        int chunks = 1 << factor;
        int blocks = chunks << 4;
        int borderBlocks = borderChunks << 4;
        return make(blocks, borderBlocks);
    }
}
