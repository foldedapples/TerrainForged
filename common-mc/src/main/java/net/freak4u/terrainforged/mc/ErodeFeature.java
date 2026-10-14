package net.freak4u.terrainforged.mc;

import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.SurfaceDecorator;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * TerraForged's erosion decorator (port of ReTerraForged 1.20.1 {@code ErodeFeature}, MIT): exposes bare stone on
 * steep and high ground, coarse dirt/gravel on moderate slopes and gravel scree where droplet erosion left
 * sediment. Runs first in {@code raw_generation}; a no-op in worlds that do not use TerrainForged.
 */
public final class ErodeFeature extends Feature<NoneFeatureConfiguration> {
    public ErodeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        TerrainEngine engine = Engines.of(level.getLevel());
        if (engine == null || !engine.decorateErosion()) return false;
        SurfaceDecorator decorator = engine.surfaceDecorator();
        int chunkX = ctx.origin().getX() >> 4, chunkZ = ctx.origin().getZ() >> 4;
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
        int seaLevel = ctx.chunkGenerator().getSeaLevel();
        BlockState defaultBlock = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = (chunkX << 4) + dx, z = (chunkZ << 4) + dz;
                ColumnSample cell = engine.sample(x, z);
                int scaledY = (int) Math.floor(cell.height);
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz);
                if (surfaceY > scaledY || surfaceY < seaLevel - 1) continue;
                Holder<Biome> biome = level.getBiome(pos.set(x, surfaceY, z));
                if (biome.is(Biomes.WOODED_BADLANDS) || biome.is(Biomes.BADLANDS)) continue;
                if (!decorator.applies(cell)) continue;
                pos.set(x, surfaceY, z);
                BlockState top = chunk.getBlockState(pos);
                if (!erodible(top)) continue;
                boolean snowy = top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.POWDER_SNOW) || DecorateSnowFeature.cold(level, pos, seaLevel);
                SurfaceDecorator.Material m = snowy ? decorator.snowyMaterial(cell, x, z) : decorator.material(cell, x, z);
                if (m == SurfaceDecorator.Material.ROCK) {
                    erodeRock(chunk, pos, surfaceY);
                    continue;
                }
                if (m == SurfaceDecorator.Material.GROUND) {
                    fillDownSolid(chunk, pos, surfaceY, surfaceY - 4, ground(top));
                }
                if (!snowy && decorator.scree(cell, x, z)) {
                    fillDownSolid(chunk, pos, surfaceY, surfaceY - 2, Blocks.GRAVEL.defaultBlockState());
                }
            }
        }
        return true;
    }

    /** RTF tag {@code erodible}: snow block, powder snow, gravel and #dirt. */
    private static boolean erodible(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.GRAVEL);
    }

    /** RTF tag {@code rock}: stone, granite, andesite, diorite. */
    private static boolean rock(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.GRANITE) || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE);
    }

    /** RTF erodeRock: strip the soil down to the first rock layer (gravel if none within 32 blocks). */
    private static void erodeRock(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y) {
        int depth = 32;
        BlockState material = Blocks.GRAVEL.defaultBlockState();
        for (int dy = 3; dy < 32; dy++) {
            pos.setY(y - dy);
            BlockState state = chunk.getBlockState(pos);
            if (rock(state)) {
                material = state;
                depth = dy + 1;
                break;
            }
        }
        for (int dy = 0; dy < depth; dy++) replaceSolid(chunk, pos.setY(y - dy), material);
    }

    private static BlockState ground(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM)) return Blocks.COARSE_DIRT.defaultBlockState();
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) return Blocks.GRAVEL.defaultBlockState();
        if (state.is(BlockTags.DIRT)) return state;
        if (state.is(Blocks.SAND)) return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        if (state.is(Blocks.RED_SAND)) return Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
        return Blocks.COARSE_DIRT.defaultBlockState();
    }

    static void fillDownSolid(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int from, int to, BlockState state) {
        for (int dy = from; dy > to; dy--) replaceSolid(chunk, pos.setY(dy), state);
    }

    static void replaceSolid(ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (chunk.getBlockState(pos).isAir()) return;
        chunk.setBlockState(pos, state, 0);
    }
}
