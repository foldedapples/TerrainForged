// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.settings;


import net.freak4u.terrainforged.engine.rtf.GeneratorContext;
import net.freak4u.terrainforged.engine.rtf.cell.continent.Continent;
import net.freak4u.terrainforged.engine.rtf.cell.continent.advanced.AdvancedContinentGenerator;
import net.freak4u.terrainforged.engine.rtf.cell.continent.fancy.FancyContinentGenerator;
import net.freak4u.terrainforged.engine.rtf.cell.continent.infinite.InfiniteContinentGenerator;
import net.freak4u.terrainforged.engine.rtf.cell.continent.simple.MultiContinentGenerator;
import net.freak4u.terrainforged.engine.rtf.cell.continent.simple.SingleContinentGenerator;
import net.freak4u.terrainforged.engine.rtf.util.Seed;

public enum ContinentType {
    MULTI {
        
    	@Override
        public MultiContinentGenerator create(Seed seed, GeneratorContext context) {
            return new MultiContinentGenerator(seed, context);
        }
    }, 
    SINGLE {
        
    	@Override
        public SingleContinentGenerator create(Seed seed, GeneratorContext context) {
            return new SingleContinentGenerator(seed, context);
        }
    }, 
    MULTI_IMPROVED {
        
    	@Override
        public AdvancedContinentGenerator create(Seed seed, GeneratorContext context) {
            return new AdvancedContinentGenerator(seed, context);
        }
    }, 
    EXPERIMENTAL {
        
    	@Override
        public FancyContinentGenerator create(Seed seed, GeneratorContext context) {
            return new FancyContinentGenerator(seed, context);
        }
    },
    INFINITE {
        
    	@Override
        public InfiniteContinentGenerator create(Seed seed, GeneratorContext context) {
            return new InfiniteContinentGenerator(context);
        }
    };
	
    
    public abstract Continent create(Seed seed, GeneratorContext context);

}
