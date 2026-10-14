package net.freak4u.terrainforged.mc;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Headless integration test against the real Minecraft 1.21.11 classes (no loader, no game window):
 * bootstraps vanilla registries, decodes TerrainForged's world preset noise settings exactly as the game does,
 * builds a {@link RandomState} and {@link NoiseBasedChunkGenerator}, then checks that
 * <ul>
 *   <li>the router wires the engine (seed derivation via visitNoise works),</li>
 *   <li>the generated surface (vanilla noise interpolation + caves + aquifers) follows the engine height,</li>
 *   <li>caves still carve the underground, oceans fill with water,</li>
 *   <li>the vanilla multi-noise biome source picks a diverse, terrain-appropriate set of biomes,</li>
 *   <li>re-encoding the settings stores concrete values (no config marker) for level.dat.</li>
 * </ul>
 * Usage: java ... HeadlessWorldgenTest &lt;preset json path&gt; [seed]
 */
public final class HeadlessWorldgenTest {
    private static int failed;

    public static void main(String[] args) throws Exception {
        String presetPath = args[0];
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 12345L;
        // "quick": 300 surface columns instead of 2000 (each new 128-block tile costs ~0.5 s to erode)
        int surfaceColumns = args.length > 2 && args[2].equals("quick") ? 300 : 2000;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // test-only: the loaders register before the freeze; here we briefly unfreeze the built-in registry
        java.lang.reflect.Field frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.DENSITY_FUNCTION_TYPE, false);
        net.minecraft.core.Holder.Reference<?> ref = Registry.registerForHolder(BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            TerrainForgedMod.id(TerrainForgedMod.DENSITY_FUNCTION_ID), TerrainForgedMod.DENSITY_FUNCTION_CODEC);
        java.lang.reflect.Method bindValue = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
        bindValue.setAccessible(true);
        bindValue.invoke(ref, TerrainForgedMod.DENSITY_FUNCTION_CODEC);
        java.lang.reflect.Method bindTags = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        bindTags.invoke(ref, java.util.List.of());
        frozen.setBoolean(BuiltInRegistries.DENSITY_FUNCTION_TYPE, true);
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        RegistryOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);

        JsonObject preset;
        try (Reader r = new InputStreamReader(new java.io.FileInputStream(presetPath), StandardCharsets.UTF_8)) {
            preset = JsonParser.parseReader(r).getAsJsonObject();
        }
        JsonObject gen = preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
        // the vanilla bootstrap lookup has no data-pack noises, so point the seed probe at a vanilla noise key;
        // the wiring path (visitNoise of a registered key) is identical
        String settingsJson = gen.get("settings").toString().replace("terrainforged:seed_probe", "minecraft:surface");
        NoiseGeneratorSettings settings = NoiseGeneratorSettings.DIRECT_CODEC.parse(ops, JsonParser.parseString(settingsJson)).getOrThrow();
        int minY = settings.noiseSettings().minY(), height = settings.noiseSettings().height();
        System.out.printf("decoded noise settings: min_y=%d height=%d sea=%d%n", minY, height, settings.seaLevel());

        // persistence: encoding must write concrete values, never the from_config marker
        EngineDensityFunction raw = EngineDensityFunction.unwrap(settings.noiseRouter().preliminarySurfaceLevel());
        String encoded = SettingsCodec.CODEC.encodeStart(JsonOps.INSTANCE, raw.settings()).getOrThrow().toString();
        check("encoded settings are concrete (no from_config marker) for level.dat",
            !encoded.contains("from_config") && encoded.contains("world.terrain_height"), encoded.length() + " chars");

        // world-creation editor path: swap settings inside a generator, encode as level.dat would, decode again
        {
            MultiNoiseBiomeSource src = MultiNoiseBiomeSource.createFromPreset(
                lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            NoiseBasedChunkGenerator original = new NoiseBasedChunkGenerator(src, Holder.direct(settings));
            check("editor finds the engine settings in the preset generator", GeneratorSettingsUtil.findEngineFunction(original) != null, "");
            TerrainSettings edited = raw.settings().with(TerrainSettings.Key.RIVER_COUNT, 14).with(TerrainSettings.Key.SEA_LEVEL, 70).withSpawn(321, -654);
            ChunkGenerator swapped = GeneratorSettingsUtil.withSettings(original, edited);
            JsonElement json = ChunkGenerator.CODEC.encodeStart(ops, swapped).getOrThrow();
            String js = json.toString();
            int engineFns = js.split("terrainforged:engine", -1).length - 1;
            check("edited generator encodes the new values in every engine function",
                js.contains("\"rivers.river_count\":14.0") && !js.contains("\"rivers.river_count\":8.0") && engineFns >= 8,
                engineFns + " engine functions, " + js.length() + " chars");
            ChunkGenerator back = ChunkGenerator.CODEC.parse(ops, json).getOrThrow();
            EngineDensityFunction f = GeneratorSettingsUtil.findEngineFunction(back);
            int seaBack = ((NoiseBasedChunkGenerator) back).generatorSettings().value().seaLevel();
            check("edited settings survive a level.dat round trip (incl. vanilla sea level)",
                f != null && f.settings().equals(edited.fitToWorld(f.minY(), f.maxY())) && seaBack == 70, "sea " + seaBack);
            // a world saved by 0.5 or earlier has no stream keys: it must keep generating without streams
            Map<String, Double> oldWorld = new HashMap<>(TerrainSettings.defaults().toMap());
            for (TerrainSettings.Key k : TerrainSettings.Key.values()) if (k.id.startsWith("rivers.streams.")) oldWorld.remove(k.id);
            JsonObject oldJson = new JsonObject();
            oldWorld.forEach(oldJson::addProperty);
            TerrainSettings oldDecoded = SettingsCodec.CODEC.parse(JsonOps.INSTANCE, oldJson).getOrThrow();
            TerrainSettings newDecoded = SettingsCodec.CODEC.parse(JsonOps.INSTANCE, SettingsCodec.CODEC.encodeStart(JsonOps.INSTANCE, TerrainSettings.defaults()).getOrThrow()).getOrThrow();
            check("worlds saved before 0.6.0 (no stream settings) load with streams off; new worlds keep them",
                oldDecoded.get(TerrainSettings.Key.STREAM_CHANCE) == 0 && newDecoded.get(TerrainSettings.Key.STREAM_CHANCE) == TerrainSettings.Key.STREAM_CHANCE.def,
                "old " + oldDecoded.get(TerrainSettings.Key.STREAM_CHANCE) + ", new " + newDecoded.get(TerrainSettings.Key.STREAM_CHANCE));
            check("chosen spawn point survives the level.dat round trip",
                f != null && f.settings().hasSpawn() && f.settings().spawnX() == 321 && f.settings().spawnZ() == -654, "");
        }

        RandomState rs = RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
        TerrainEngine engine = Engines.of(rs);
        check("RandomState wires the engine through visitNoise", engine != null, String.valueOf(engine));
        RandomState rs2 = RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
        TerrainEngine engine2 = Engines.of(rs2);
        check("same seed -> same shared engine", engine2 == engine, "");
        RandomState rs3 = RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed + 1);
        TerrainEngine engine3 = Engines.of(rs3);
        check("different seed -> different engine seed", engine3 != null && engine3.seed() != engine.seed(), engine.seed() + " vs " + (engine3 == null ? "null" : engine3.seed()));
        if (engine != null) { // spawn picked in water moves to the nearest dry land; dry picks stay exactly where they are
            int sea = engine.settings().seaLevel(), wetX = Integer.MIN_VALUE, wetZ = 0, dryX = Integer.MIN_VALUE, dryZ = 0;
            for (int i = 0; i < 400 && (wetX == Integer.MIN_VALUE || dryX == Integer.MIN_VALUE); i++) {
                int x = (i % 20) * 160 - 1600, z = (i / 20) * 160 - 1600;
                boolean dry = SpawnPlacer.isDry(engine.sample(x, z), sea);
                if (!dry && wetX == Integer.MIN_VALUE && SpawnPlacer.isDry(engine.sample(x + 96, z), sea)) { wetX = x; wetZ = z; }
                if (dry && dryX == Integer.MIN_VALUE) { dryX = x; dryZ = z; }
            }
            int[] moved = wetX == Integer.MIN_VALUE ? null : SpawnPlacer.findDryLand(engine, wetX, wetZ, sea);
            int[] kept = dryX == Integer.MIN_VALUE ? null : SpawnPlacer.findDryLand(engine, dryX, dryZ, sea);
            boolean movedOk = moved != null && SpawnPlacer.isDry(engine.sample(moved[0], moved[1]), sea)
                && Math.abs(moved[0] - wetX) <= SpawnPlacer.SEARCH_RADIUS && Math.abs(moved[1] - wetZ) <= SpawnPlacer.SEARCH_RADIUS;
            boolean keptOk = kept != null && kept[0] == dryX && kept[1] == dryZ;
            check("spawn placement: water picks move to nearby dry land, dry picks are kept", movedOk && keptOk,
                moved == null ? "no wet test point found" : "wet " + wetX + "," + wetZ + " -> " + moved[0] + "," + moved[1]);
        }
        if (engine == null) {
            System.exit(1);
        }

        Holder<Biome> dummy = null;
        MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(
            lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, Holder.direct(settings));
        LevelHeightAccessor level = LevelHeightAccessor.create(minY, height);
        int sea = settings.seaLevel();

        // ---- surface follows the engine
        int n = 0, cave = 0, oceanCols = 0, oceanWater = 0, landCols = 0;
        double[] diffs = new double[surfaceColumns];
        long t0 = System.nanoTime();
        for (int i = 0; i < surfaceColumns; i++) {
            // 12 patches of 512x512 blocks spread over the world (tiles are 128 blocks; keep the tile count sane)
            int patch = i % 12;
            int px = (int) ((patch * 7919L) % 24000) - 12000, pz = (int) ((patch * 104729L) % 24000) - 12000;
            int x = px + (int) ((i * 31L) % 512), z = pz + (int) ((i * 57L) % 512);
            ColumnSample c = engine.sample(x, z);
            int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1;
            diffs[n++] = Math.abs(h - c.height);
            NoiseColumn col = generator.getBaseColumn(x, z, level, rs);
            if (c.height < sea - 2) {
                oceanCols++;
                if (col.getBlock(sea - 1).is(Blocks.WATER)) oceanWater++;
            } else {
                landCols++;
                for (int y = minY + 8; y < h - 20; y++) {
                    BlockState s = col.getBlock(y);
                    if (s.isAir()) { cave++; break; }
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Arrays.sort(diffs);
        double mean = Arrays.stream(diffs).average().orElse(99);
        check("generated surface matches engine height (vanilla 4x8 interpolation + caves allowed)",
            mean < 2.0 && diffs[(int) (n * 0.9)] <= 4.0,
            String.format("mean |dy| %.2f, p50 %.1f, p90 %.1f, p99 %.1f (%d columns, %d ms incl. base columns)", mean, diffs[n / 2], diffs[(int) (n * 0.9)], diffs[(int) (n * 0.99)], n, ms));
        check("oceans are filled with water by vanilla aquifers", oceanCols == 0 || oceanWater > oceanCols * 0.9, oceanWater + "/" + oceanCols);
        check("vanilla caves still carve the underground", cave > landCols * 0.15, cave + " of " + landCols + " land columns have a cave/air pocket");

        // ---- RTF-style water: river and lake beds are carved below sea level and must be filled by the aquifers
        int inlandWater = 0, inlandWet = 0;
        for (int z = -8000; z <= 8000 && inlandWater < 150; z += 37) {
            for (int x = -8000; x <= 8000 && inlandWater < 150; x += 37) {
                ColumnSample rawCol = engine.preview(x, z);
                if (!(rawCol.is(net.freak4u.terrainforged.engine.terrain.RtfTerrain.FLAG_RIVER) || rawCol.is(net.freak4u.terrainforged.engine.terrain.RtfTerrain.FLAG_LAKE))
                    || rawCol.riverMask > 0.05f) continue;
                ColumnSample c = engine.sample(x, z);
                if (c.height >= sea - 2) continue;
                inlandWater++;
                NoiseColumn col = generator.getBaseColumn(x, z, level, rs);
                // every block between the generated bed and the sea surface must be water (no air pockets)
                boolean filled = true;
                for (int y = sea - 1; y > minY; y--) {
                    BlockState b = col.getBlock(y);
                    if (b.is(Blocks.WATER)) continue;
                    if (b.isAir()) filled = false;
                    break;
                }
                if (filled && col.getBlock(sea - 1).is(Blocks.WATER)) inlandWet++;
            }
        }
        check("river and lake channels are filled with water by vanilla aquifers", inlandWater >= 20 && inlandWet >= inlandWater * 0.9,
            inlandWet + "/" + inlandWater + " carved channel columns hold water up to sea level");

        // ---- mountain streams on real generated terrain (vanilla noise, caves and aquifers): plan 3x3 chunks around a
        // stream from the real blocks, as StreamFeature does, apply it to a copy and check every placed water block
        streamsOnRealTerrain(engine, generator, level, rs, minY, sea);

        // ---- biomes
        Map<String, Integer> counts = new TreeMap<>();
        // 24 fixed patches (mostly ocean on some seeds) plus 12 patches centred on high ground, found with the fast
        // raw-cell preview, so mountain biomes are sampled whatever the continent layout of the seed
        List<int[]> patches = new ArrayList<>();
        for (int patch = 0; patch < 24; patch++) patches.add(new int[]{(int) ((patch * 7919L) % 40000) - 20000, (int) ((patch * 104729L) % 40000) - 20000});
        for (int i = 0, found = 0; i < 20000 && found < 12; i++) {
            int x = (int) ((i * 6029L) % 60000) - 30000, z = (int) ((i * 9719L) % 60000) - 30000;
            if (engine.preview(x, z).height > sea + 100) { patches.add(new int[]{x - 240, z - 240}); found++; }
        }
        for (int[] patch : patches) {
            int px = patch[0], pz = patch[1];
            for (int k = 0; k < 400; k++) {
                int x = px + (k % 20) * 24, z = pz + (k / 20) * 24;
                ColumnSample c = engine.sample(x, z);
                int y = (int) Math.max(c.height, sea);
                Holder<Biome> b = biomes.getNoiseBiome(x >> 2, y >> 2, z >> 2, rs.sampler());
                counts.merge(b.unwrapKey().map(key -> key.identifier().getPath()).orElse("?"), 1, Integer::sum);
            }
        }
        System.out.println("surface biome distribution: " + counts);
        boolean hasOcean = counts.keySet().stream().anyMatch(k -> k.contains("ocean"));
        boolean hasMountain = counts.keySet().stream().anyMatch(k -> k.contains("peaks") || k.contains("slopes") || k.contains("windswept"));
        boolean hasRiver = counts.containsKey("river") || counts.containsKey("frozen_river");
        int sampled = counts.values().stream().mapToInt(Integer::intValue).sum();
        int mushroom = counts.getOrDefault("mushroom_fields", 0);
        int shore = counts.getOrDefault("beach", 0) + counts.getOrDefault("snowy_beach", 0) + counts.getOrDefault("stony_shore", 0);
        check("mushroom fields stay rare (deep ocean must not map to them)", mushroom * 100 < sampled, mushroom + " of " + sampled);
        check("coasts get beach / stony shore biomes", shore > 0, shore + " shore samples");
        int max = counts.values().stream().max(Integer::compare).orElse(0), total = counts.values().stream().mapToInt(Integer::intValue).sum();
        check("biome diversity with oceans, mountains and rivers", counts.size() >= 20 && hasOcean && hasMountain && hasRiver && max < total * 0.4,
            counts.size() + " biomes; largest share " + (100 * max / Math.max(1, total)) + "%");

        System.out.println(failed == 0 ? "ALL HEADLESS CHECKS PASSED" : failed + " HEADLESS CHECK(S) FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void streamsOnRealTerrain(TerrainEngine engine, NoiseBasedChunkGenerator generator, LevelHeightAccessor level,
                                             RandomState rs, int minY, int sea) {
        // most mountainous spot on a coarse grid, then the longest stream starting near it
        int mx = 0, mz = 0;
        double best = -1;
        for (int x = -8192; x <= 8192; x += 2048) {
            for (int z = -8192; z <= 8192; z += 2048) {
                double sum = 0;
                for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) sum += Math.max(0, Math.min(80, engine.preview(x + i * 160, z + j * 160).height - sea));
                if (sum > best) { best = sum; mx = x; mz = z; }
            }
        }
        net.freak4u.terrainforged.engine.worldgen.StreamNetwork.Path longest = null;
        for (var p : engine.streams().pathsStartingIn(mx - 768, mz - 768, mx + 768, mz + 768)) if (longest == null || p.length > longest.length) longest = p;
        if (longest == null) {
            check("mountain streams on real generated terrain", false, "no stream found near " + mx + "," + mz);
            return;
        }
        int mid = longest.length / 3;
        int x0 = ((longest.x[mid] >> 4) - 1) << 4, z0 = ((longest.z[mid] >> 4) - 1) << 4, size = 48, halo = 4;
        int W = size + 2 * halo;
        BlockState[][] cols = new BlockState[W * W][];
        for (int dz = 0; dz < W; dz++) {
            for (int dx = 0; dx < W; dx++) {
                NoiseColumn c = generator.getBaseColumn(x0 - halo + dx, z0 - halo + dz, level, rs);
                BlockState[] col = new BlockState[level.getHeight()];
                for (int y = 0; y < col.length; y++) col[y] = c.getBlock(minY + y);
                cols[dz * W + dx] = col;
            }
        }
        java.util.function.BiFunction<Integer, Integer, BlockState[]> column = (x, z) -> cols[(z - z0 + halo) * W + (x - x0 + halo)];
        net.freak4u.terrainforged.engine.worldgen.StreamCarver.Terrain terrain = new net.freak4u.terrainforged.engine.worldgen.StreamCarver.Terrain() {
            public int surfaceTop(int x, int z) {
                BlockState[] col = column.apply(x, z);
                int y = col.length - 1;
                while (y > 0 && col[y].isAir()) y--;
                return minY + y;
            }

            public int solidTop(int x, int z) {
                BlockState[] col = column.apply(x, z);
                int y = surfaceTop(x, z) - minY;
                while (y > 0 && !(col[y].blocksMotion() && col[y].getFluidState().isEmpty())) y--;
                return minY + y;
            }
        };
        int NONE = net.freak4u.terrainforged.engine.worldgen.StreamCarver.NONE;
        int[] water = new int[size * size], bed = new int[size * size];
        Arrays.fill(water, NONE);
        for (int cz = 0; cz < size; cz += 16) {
            for (int cx = 0; cx < size; cx += 16) {
                var plan = net.freak4u.terrainforged.engine.worldgen.StreamCarver.plan(engine.streams(), x0 + cx, z0 + cz, 16, 16, terrain);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int i = z * 16 + x, k = (cz + z) * size + cx + x;
                        water[k] = plan.water[i];
                        bed[k] = plan.bed[i];
                    }
                }
            }
        }
        // apply like StreamFeature (air above, water down to the bed) to the copy
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int k = z * size + x;
                if (water[k] == NONE) continue;
                BlockState[] col = column.apply(x0 + x, z0 + z);
                int top = terrain.solidTop(x0 + x, z0 + z);
                for (int y = top; y > water[k]; y--) col[y - minY] = Blocks.AIR.defaultBlockState();
                for (int y = water[k]; y > bed[k]; y--) col[y - minY] = Blocks.WATER.defaultBlockState();
                col[bed[k] - minY] = Blocks.GRAVEL.defaultBlockState();
            }
        }
        // every water block: each horizontal neighbour is water, or solid, at the same height (no open side)
        int blocks = 0, columns = 0, open = 0, caveOpen = 0, falls = 0;
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 1; z < size - 1; z++) {
            for (int x = 1; x < size - 1; x++) {
                int k = z * size + x;
                if (water[k] == NONE) continue;
                columns++;
                for (int y = water[k]; y > bed[k]; y--) {
                    blocks++;
                    for (int[] d : nb) {
                        int nk = (z + d[1]) * size + x + d[0];
                        BlockState n = column.apply(x0 + x + d[0], z0 + z + d[1])[y - minY];
                        if (!n.isAir()) continue;
                        if (water[nk] != NONE) { if (y > water[nk]) falls++; continue; } // pours into the next stream column
                        // open side: is it a cave opening (solid above it in that column) or open land?
                        if (terrain.solidTop(x0 + x + d[0], z0 + z + d[1]) > y) caveOpen++;
                        else open++;
                    }
                }
            }
        }
        check("mountain streams on real generated terrain: every water block is walled in or pours into the stream",
            columns > 20 && open == 0,
            String.format("stream near %d,%d: %d water columns, %d water blocks, %d sides pouring into lower stream columns (waterfalls/rapids), "
                + "%d open to the land, %d into cave openings", x0 + 24, z0 + 24, columns, blocks, falls, open, caveOpen));
    }

    private static void check(String name, boolean ok, String detail) {
        if (!ok) failed++;
        System.out.printf("%s  %s  [%s]%n", ok ? "PASS" : "FAIL", name, detail);
    }
}
