// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.noise.function;



public enum EdgeFunction {
	DISTANCE_2("DISTANCE_2") {

		@Override
		public float apply(float distance, float distance2) {
			return distance2 - 1.0F;
		}

		@Override
		public float max() {
			return 1.0F;
		}

		@Override
		public float min() {
			return -1.0F;
		}

		@Override
		public float range() {
			return 2.0F;
		}
	},
	DISTANCE_2_ADD("DISTANCE_2_ADD") {

		@Override
		public float apply(float distance, float distance2) {
			return distance2 + distance - 1.0F;
		}

		@Override
		public float max() {
			return 1.6F;
		}

		@Override
		public float min() {
			return -1.0F;
		}

		@Override
		public float range() {
			return 2.6F;
		}
	},
	DISTANCE_2_SUB("DISTANCE_2_SUB") {

		@Override
		public float apply(float distance, float distance2) {
			return distance2 - distance - 1.0F;
		}

		@Override
		public float max() {
			return 0.8F;
		}

		@Override
		public float min() {
			return -1.0F;
		}

		@Override
		public float range() {
			return 1.8F;
		}
	},
	DISTANCE_2_MUL("DISTANCE_2_MUL") {

		@Override
		public float apply(float distance, float distance2) {
			return distance2 * distance - 1.0F;
		}

		@Override
		public float max() {
			return 0.7F;
		}

		@Override
		public float min() {
			return -1.0F;
		}

		@Override
		public float range() {
			return 1.7F;
		}
	},
	DISTANCE_2_DIV("DISTANCE_2_DIV") {

		@Override
		public float apply(float distance, float distance2) {
			return distance / distance2 - 1.0F;
		}

		@Override
		public float max() {
			return 0.0F;
		}

		@Override
		public float min() {
			return -1.0F;
		}

		@Override
		public float range() {
			return 1.0F;
		}
	};


	private String name;

	private EdgeFunction(String name) {
		this.name = name;
	}


	public abstract float apply(float distance, float distance2);

	public abstract float max();

	public abstract float min();

	public abstract float range();
}
