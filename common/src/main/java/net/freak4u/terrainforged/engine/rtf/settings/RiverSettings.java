// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.settings;


public class RiverSettings {
	
    public int seedOffset;
    public int riverCount;
    public River mainRivers;
    public River branchRivers;
    public Lake lakes;
    public Wetland wetlands;
    
    public RiverSettings(int seedOffset, int riverCount, River mainRivers, River branchRivers, Lake lakes, Wetland wetlands) {
        this.seedOffset = seedOffset;
        this.riverCount = riverCount;
        this.mainRivers = mainRivers;
        this.branchRivers = branchRivers;
        this.lakes = lakes;
        this.wetlands = wetlands;
    }
    
    public RiverSettings copy() {
    	return new RiverSettings(this.seedOffset, this.riverCount, this.mainRivers.copy(), this.branchRivers.copy(), this.lakes.copy(), this.wetlands.copy());
    }

    public static class River {
    	
        public int bedDepth;
        public int minBankHeight;
        public int maxBankHeight;
        public int bedWidth;
        public int bankWidth;
        public float fade;
        
        public River(int bedDepth, int minBankHeight, int maxBankHeight, int bankWidth, int bedWidth, float fade) {
            this.bedDepth = bedDepth;
            this.minBankHeight = minBankHeight;
            this.maxBankHeight = maxBankHeight;
            this.bankWidth = bankWidth;
            this.bedWidth = bedWidth;
            this.fade = fade;
        }
        
        public River copy() {
        	return new River(this.bedDepth, this.minBankHeight, this.maxBankHeight, this.bankWidth, this.bedWidth, this.fade);
        }
    }
    
    public static class Lake {

        public float chance;
        public float minStartDistance;
        public float maxStartDistance;
        public int depth;
        public int sizeMin;
        public int sizeMax;
        public int minBankHeight;
        public int maxBankHeight;
        
        public Lake(float chance, float minStartDistance, float maxStartDistance, int depth, int sizeMin, int sizeMax, int minBankHeight, int maxBankHeight) {
        	this.chance = chance;
        	this.minStartDistance = minStartDistance;
        	this.maxStartDistance = maxStartDistance;
        	this.depth = depth;
        	this.sizeMin= sizeMin;
        	this.sizeMax = sizeMax;
        	this.minBankHeight = minBankHeight;
        	this.maxBankHeight = maxBankHeight;
        }
        
        public Lake copy() {
        	return new Lake(this.chance, this.minStartDistance, this.maxStartDistance, this.depth, this.sizeMin, this.sizeMax, this.minBankHeight, this.maxBankHeight);
        }
    }
    
    public static class Wetland {
    	
        public float chance;
        public int sizeMin;
        public int sizeMax;
        
        public Wetland(float chance, int sizeMin, int sizeMax) {
        	this.chance = chance;
        	this.sizeMin = sizeMin;
        	this.sizeMax = sizeMax;
        }
        
        public Wetland copy() {
        	return new Wetland(this.chance, this.sizeMin, this.sizeMax);
        }
    }
}
