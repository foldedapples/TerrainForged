package net.freak4u.terrainforged.mc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.freak4u.terrainforged.engine.api.EngineRegistry;
import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.noise.Hash;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * {@code terrainforged:engine} density function: exposes one field of the TerrainForged engine to the vanilla
 * noise router. Used for {@code final_density} (terrain), {@code preliminary_surface_level}, {@code depth} and the
 * five multi-noise climate coordinates, so vanilla caves, aquifers, ore veins, surface rules, structures and the
 * overworld biome table keep working on top of TerrainForged terrain.
 *
 * <p><b>Seed without mixins:</b> density functions do not receive the world seed. This function carries a
 * registered noise ({@code terrainforged:seed_probe}); when {@code RandomState} wires the router it instantiates
 * that noise from the world seed via {@code visitNoise}, and a hash of a few fixed samples of it becomes the engine
 * seed. Every TerrainForged function in a world sees the same noise, hence the same engine.
 */
public final class EngineDensityFunction implements DensityFunction.SimpleFunction {

    public enum Field implements StringRepresentable {
        TERRAIN("terrain"), HEIGHT("height"), DEPTH("depth"),
        CONTINENTALNESS("continentalness"), EROSION("erosion"), WEIRDNESS("weirdness"),
        TEMPERATURE("temperature"), HUMIDITY("humidity");

        public static final Codec<Field> CODEC = StringRepresentable.fromEnum(Field::values);
        private final String name;

        Field(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Density gradient per block for the terrain field (vanilla's sloped_cheese has a similar slope). */
    public static final double TERRAIN_SLOPE = 0.08;

    public static final MapCodec<EngineDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Field.CODEC.fieldOf("field").forGetter(EngineDensityFunction::field),
        NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(f -> f.seedNoise),
        SettingsCodec.CODEC.fieldOf("settings").forGetter(f -> f.settings),
        Codec.INT.fieldOf("min_y").forGetter(f -> f.minY),
        Codec.INT.fieldOf("max_y").forGetter(f -> f.maxY)
    ).apply(i, EngineDensityFunction::new));
    public static final KeyDispatchDataCodec<EngineDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final Field field;
    private final NoiseHolder seedNoise;
    private final TerrainSettings settings;
    private final int minY, maxY;
    private final TerrainEngine engine; // null until wired by RandomState

    public EngineDensityFunction(Field field, NoiseHolder seedNoise, TerrainSettings settings, int minY, int maxY) {
        this(field, seedNoise, settings.fitToWorld(minY, maxY), minY, maxY, null);
    }

    private EngineDensityFunction(Field field, NoiseHolder seedNoise, TerrainSettings settings, int minY, int maxY, TerrainEngine engine) {
        this.field = field;
        this.seedNoise = seedNoise;
        this.settings = settings;
        this.minY = minY;
        this.maxY = maxY;
        this.engine = engine;
    }

    public Field field() { return field; }
    public TerrainEngine engine() { return engine; }
    public TerrainSettings settings() { return settings; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }

    /** A copy bound to other settings (used by the world-creation editor before the world exists). */
    public EngineDensityFunction withSettings(TerrainSettings newSettings) {
        return new EngineDensityFunction(field, seedNoise, newSettings, minY, maxY);
    }

    @Override
    public double compute(FunctionContext ctx) {
        TerrainEngine e = engine;
        if (e == null) return fallback(ctx);
        ColumnSample c = e.sample(ctx.blockX(), ctx.blockZ());
        return switch (field) {
            case TERRAIN -> (c.height - ctx.blockY()) * TERRAIN_SLOPE;
            case HEIGHT -> aquiferSurface(c.height);
            case DEPTH -> (c.height - ctx.blockY()) / 128.0;
            case CONTINENTALNESS -> c.vContinentalness;
            case EROSION -> c.vErosion;
            case WEIRDNESS -> c.vWeirdness;
            case TEMPERATURE -> c.vTemperature;
            case HUMIDITY -> c.vHumidity;
        };
    }

    /** How far {@link #aquiferSurface} reports the surface below the real terrain. */
    public static final int AQUIFER_SURFACE_BIAS = 16;

    /**
     * Value for the router's {@code preliminary_surface_level}, which vanilla only uses for aquifers (and the
     * surface rules' "above preliminary surface" gate). ReTerraForged lowers its initial density everywhere (by
     * 90/128 units, roughly 5-20 blocks depending on the terrain factor) so that terrain carved below sea level - river
     * beds, lakes, coasts - is filled with sea-level water: aquifer grid points on the river banks then also see a
     * surface under the sea and pick the global (sea-level) fluid. TerrainForged does the same with a fixed 16-block
     * bias.
     */
    private double aquiferSurface(double height) {
        return height - AQUIFER_SURFACE_BIAS;
    }

    private double fallback(FunctionContext ctx) {
        double h = settings.seaLevel() + 1;
        return switch (field) {
            case TERRAIN -> (h - ctx.blockY()) * TERRAIN_SLOPE;
            case HEIGHT -> h;
            case DEPTH -> (h - ctx.blockY()) / 128.0;
            default -> 0.0;
        };
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        NoiseHolder mapped = visitor.visitNoise(seedNoise);
        TerrainEngine e = engine;
        if (e == null && mapped.noise() != null) {
            e = EngineRegistry.get(seedFrom(mapped.noise()), settings, minY);
        }
        return visitor.apply(new EngineDensityFunction(field, mapped, settings, minY, maxY, e));
    }

    /**
     * Router entries decoded from JSON are wrapped in {@code DensityFunctions.HolderHolder} (direct holders); this
     * finds the TerrainForged function underneath, or null.
     */
    public static EngineDensityFunction unwrap(DensityFunction f) {
        for (int i = 0; i < 8 && f != null; i++) {
            if (f instanceof EngineDensityFunction e) return e;
            if (f instanceof net.minecraft.world.level.levelgen.DensityFunctions.HolderHolder h) f = h.function().value();
            else return null;
        }
        return null;
    }

    public static long seedFrom(NormalNoise noise) {
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 6; i++) {
            double v = noise.getValue(i * 1013.37 + 0.311, i * 17.17 + 0.5, -i * 719.29 + 0.771);
            h = Hash.mix(h ^ Double.doubleToLongBits(v));
        }
        return h;
    }

    @Override
    public double minValue() {
        int span = maxY - minY;
        return switch (field) {
            case TERRAIN -> -span * TERRAIN_SLOPE;
            case HEIGHT -> minY;
            case DEPTH -> -span / 128.0;
            default -> -1.0;
        };
    }

    @Override
    public double maxValue() {
        int span = maxY - minY;
        return switch (field) {
            case TERRAIN -> span * TERRAIN_SLOPE;
            case HEIGHT -> maxY;
            case DEPTH -> span / 128.0;
            default -> 1.0;
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
