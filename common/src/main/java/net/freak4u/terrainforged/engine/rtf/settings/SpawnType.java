// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.settings;


import net.freak4u.terrainforged.engine.rtf.GeneratorContext;
import net.freak4u.terrainforged.engine.rtf.util.PosUtil;

//TODO fix this
public enum SpawnType {
    CONTINENT_CENTER("CONTINENT_CENTER") {
//    	private static final Climate.Parameter FULL_RANGE = Climate.Parameter.span(-1.0F, 1.0F);
//        private static final Climate.Parameter INLAND_CONTINENTALNESS = Climate.Parameter.point(SpawnType.CONTINENT_CENTER_NOISE_TARGET);
//		
//		@Override
//		public List<ParameterPoint> getParameterPoints() {
//			//FIXME this will spawn us on coasts as well
//		    return List.of(new Climate.ParameterPoint(FULL_RANGE, FULL_RANGE, INLAND_CONTINENTALNESS, FULL_RANGE, Climate.Parameter.point(0.0F), FULL_RANGE, 0L));
//		}
    	@Override
    	public long getSearchCenter(GeneratorContext ctx) {
    		long center = ctx.generator.getHeightmap().continent().getNearestCenter(0.0F, 0.0F);
    		return center;
    	}
	}, 
    ISLANDS("ISLANDS") {

		@Override
		public long getSearchCenter(GeneratorContext ctx) {
			return 0L;
		}
//		
//		@Override
//		public List<ParameterPoint> getParameterPoints() {
//			return ImmutableList.of();
//		}
	},
    WORLD_ORIGIN("WORLD_ORIGIN") {

		@Override
		public long getSearchCenter(GeneratorContext ctx) {
			return 0L;
		}
//		
//		@Override
//		public List<ParameterPoint> getParameterPoints() {
//			return ImmutableList.of();
//		}
	};
	
//	public static final float CONTINENT_CENTER_NOISE_TARGET = 1.0F;
	

	private String name;
	
	private SpawnType(String name) {
		this.name = name;
	}
	
	
	public abstract long getSearchCenter(GeneratorContext ctx);
	
//	public abstract List<ParameterPoint> getParameterPoints();
}