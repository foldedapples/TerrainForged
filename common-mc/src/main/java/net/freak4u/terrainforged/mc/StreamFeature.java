package net.freak4u.terrainforged.mc;

import net.freak4u.terrainforged.engine.worldgen.StreamCarver;
import net.freak4u.terrainforged.engine.worldgen.StreamNetwork;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Places TerrainForged's mountain streams and waterfalls in a chunk (decoration step {@code lakes}, so before trees
 * and plants). Where the water goes is decided by {@link StreamCarver} from the stream network and the chunk's real
 * terrain; this class only reads block heights and writes blocks: air above the water, water source blocks, and a
 * gravel bed where the stream runs over soil. Every placed water block gets a fluid tick, so where the level drops
 * the water starts flowing over the edge (waterfalls) as soon as the chunk is near a player, and nothing is left as a
 * static "water wall". A no-op in worlds that are not TerrainForged or have streams turned off. Made by Freak4U.
 */
public final class StreamFeature extends Feature<NoneFeatureConfiguration> {
    private static final int UPDATE_CLIENTS = 2;

    public StreamFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        TerrainEngine engine = Engines.of(level.getLevel());
        if (engine == null || !engine.streams().enabled()) return false;
        int x0 = (ctx.origin().getX() >> 4) << 4, z0 = (ctx.origin().getZ() >> 4) << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinY(), maxY = level.getMaxY();

        StreamCarver.Terrain terrain = new StreamCarver.Terrain() {
            @Override
            public int surfaceTop(int x, int z) {
                int start = Math.min(maxY, Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1,
                    (int) Math.floor(engine.sample(x, z).height) + 8));
                pos.set(x, start, z);
                while (pos.getY() > minY && level.getBlockState(pos).isAir()) pos.move(0, -1, 0);
                return pos.getY();
            }

            @Override
            public int solidTop(int x, int z) {
                int y = surfaceTop(x, z);
                pos.set(x, y, z);
                while (pos.getY() > minY) {
                    BlockState s = level.getBlockState(pos);
                    if (s.blocksMotion() && s.getFluidState().isEmpty()) break;
                    pos.move(0, -1, 0);
                }
                return pos.getY();
            }
        };
        StreamCarver.Plan plan = StreamCarver.plan(engine.streams(), x0, z0, 16, 16, terrain);
        if (!plan.any()) return false;

        BlockState air = Blocks.AIR.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int i = dz * 16 + dx, w = plan.water[i];
                if (w == StreamCarver.NONE) continue;
                int x = x0 + dx, z = z0 + dz;
                for (int y = plan.top[i]; y > w; y--) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, UPDATE_CLIENTS);
                }
                for (int y = w; y > plan.bed[i]; y--) {
                    pos.set(x, y, z);
                    level.setBlock(pos, water, UPDATE_CLIENTS);
                }
                pos.set(x, plan.bed[i], z);
                level.setBlock(pos, bed(level, pos), UPDATE_CLIENTS);
                pos.set(x, w, z);
                level.scheduleTick(pos, Fluids.WATER, 0);
            }
        }
        // stream water just across the chunk edge may now flow into this chunk: wake it up too
        for (int d = -1; d <= 16; d++) {
            tickEdge(level, plan, pos, x0 + d, z0 - 1);
            tickEdge(level, plan, pos, x0 + d, z0 + 16);
            if (d >= 0 && d < 16) {
                tickEdge(level, plan, pos, x0 - 1, z0 + d);
                tickEdge(level, plan, pos, x0 + 16, z0 + d);
            }
        }
        return true;
    }

    /** Gravel over soil (a natural stream bed), stone where there is nothing solid underneath, else unchanged. */
    private static BlockState bed(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
        BlockState current = level.getBlockState(pos);
        pos.move(0, -1, 0);
        boolean supported = level.getBlockState(pos).blocksMotion();
        pos.move(0, 1, 0);
        if (!supported || current.isAir() || !current.getFluidState().isEmpty()) return Blocks.STONE.defaultBlockState();
        if (current.is(BlockTags.DIRT) || current.is(BlockTags.SAND) || current.is(BlockTags.SNOW) || current.is(Blocks.CLAY)
            || current.is(Blocks.GRAVEL)) return Blocks.GRAVEL.defaultBlockState();
        return current;
    }

    private static void tickEdge(WorldGenLevel level, StreamCarver.Plan plan, BlockPos.MutableBlockPos pos, int x, int z) {
        if (!plan.isStream(x, z)) return;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        for (int y = top + 2; y >= top - StreamNetwork.MAX_CUT - 2; y--) {
            pos.set(x, y, z);
            if (level.getBlockState(pos).getFluidState().is(Fluids.WATER)) {
                level.scheduleTick(pos, Fluids.WATER, 0);
                return;
            }
        }
    }
}
