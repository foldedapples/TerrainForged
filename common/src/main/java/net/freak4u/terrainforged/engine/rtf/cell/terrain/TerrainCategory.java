// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.terrain;

public enum TerrainCategory implements ITerrain {
    NONE, 
    DEEP_OCEAN {
        @Override
        public boolean isDeepOcean() {
            return true;
        }
        
        @Override
        public boolean overridesRiver() {
            return true;
        }
        
        @Override
        public boolean isSubmerged() {
            return true;
        }
    }, 
    SHALLOW_OCEAN {
        @Override
        public boolean isShallowOcean() {
            return true;
        }
        
        @Override
        public boolean isSubmerged() {
            return true;
        }
        
        @Override
        public boolean overridesRiver() {
            return true;
        }
    }, 
    COAST {
        @Override
        public boolean isCoast() {
            return true;
        }
        
        @Override
        public boolean isOverground() {
            return true;
        }
        
        @Override
        public boolean overridesRiver() {
            return true;
        }
    }, 
    BEACH {
        @Override
        public boolean isCoast() {
            return true;
        }
        
        @Override
        public boolean isOverground() {
            return true;
        }
        
        @Override
        public boolean overridesRiver() {
            return true;
        }
    }, 
    RIVER {
        @Override
        public boolean isRiver() {
            return true;
        }
        
        @Override
        public boolean isSubmerged() {
            return true;
        }
    }, 
    LAKE {
        @Override
        public boolean isLake() {
            return true;
        }
        
        @Override
        public boolean isSubmerged() {
            return true;
        }
    }, 
    WETLAND {
        @Override
        public boolean isWetland() {
            return true;
        }
        
        @Override
        public boolean isOverground() {
            return true;
        }
    }, 
    FLATLAND {
        @Override
        public boolean isFlat() {
            return true;
        }
        
        @Override
        public boolean isOverground() {
            return true;
        }
    }, 
    LOWLAND {
        @Override
        public boolean isOverground() {
            return true;
        }
    }, 
    HIGHLAND {
        @Override
        public boolean isOverground() {
            return true;
        }
    };
    
    public TerrainCategory getDominant(TerrainCategory other) {
        if (this.ordinal() > other.ordinal()) {
            return this;
        }
        return other;
    }
}
