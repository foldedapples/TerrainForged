package net.freak4u.terrainforged.mc;

import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.SurfaceDecorator;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * TerraForged's natural-snow decorator (port of ReTerraForged 1.20.1 {@code DecorateSnowFeature}, MIT): removes snow
 * from slopes too steep to hold it in cold biomes and sets snow-layer heights from the exact terrain height, so snowy
 * slopes look smooth. Runs last in {@code top_layer_modification}; a no-op in other worlds.
 */
public final class DecorateSnowFeature extends Feature<NoneFeatureConfiguration> {
    public DecorateSnowFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        TerrainEngine engine = Engines.of(level.getLevel());
        if (engine == null || (!engine.decorateSnow() && !engine.smoothSnow())) return false;
        SurfaceDecorator decorator = engine.surfaceDecorator();
        int chunkX = ctx.origin().getX() >> 4, chunkZ = ctx.origin().getZ() >> 4;
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
        int seaLevel = ctx.chunkGenerator().getSeaLevel();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = (chunkX << 4) + dx, z = (chunkZ << 4) + dz;
                ColumnSample cell = engine.sample(x, z);
                int scaledY = (int) Math.floor(cell.height);
                int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
                if (scaledY != surfaceY || scaledY < seaLevel) continue;
                pos.set(x, surfaceY, z);
                if (engine.decorateSnow() && cold(level, pos, seaLevel) && decorator.snowErodes(cell, x, z)) {
                    for (int dy = 2; dy > 0; dy--) {
                        pos.setY(surfaceY + dy);
                        BlockState state = chunk.getBlockState(pos);
                        if (!Heightmap.Types.MOTION_BLOCKING.isOpaque().test(state) || state.is(Blocks.SNOW)) erodeSnow(chunk, pos);
                    }
                }
                if (engine.smoothSnow()) {
                    pos.setY(surfaceY + 1);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) {
                        pos.setY(surfaceY);
                        state = chunk.getBlockState(pos);
                        if (state.isAir()) continue;
                    }
                    if (state.is(Blocks.SNOW)) smoothSnow(chunk, pos, decorator.snowLayers(cell));
                }
            }
        }
        return true;
    }

    static boolean cold(WorldGenLevel level, BlockPos pos, int seaLevel) {
        var biome = level.getBiome(pos).value();
        return biome.getBaseTemperature() <= 0.25F || biome.coldEnoughToSnow(pos, seaLevel);
    }

    private static void erodeSnow(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), 0);
        pos.setY(pos.getY() - 1);
        BlockState below = chunk.getBlockState(pos);
        if (below.hasProperty(SnowyDirtBlock.SNOWY)) chunk.setBlockState(pos, below.setValue(SnowyDirtBlock.SNOWY, false), 0);
    }

    private static void smoothSnow(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int level) {
        if (level < 1) return;
        BlockState layer = level >= 8 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, level);
        chunk.setBlockState(pos, layer, 0);
        if (level > 1 && layer.is(Blocks.SNOW)) {
            BlockPos below = pos.below();
            if (chunk.getBlockState(below).getBlock() instanceof SpreadingSnowyDirtBlock) chunk.setBlockState(below, Blocks.DIRT.defaultBlockState(), 0);
        }
    }
}
