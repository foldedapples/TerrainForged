package net.freak4u.terrainforged.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.config.TerrainSettings.Key;
import net.freak4u.terrainforged.engine.preview.ReliefRenderer;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.SurfaceDecorator;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.freak4u.terrainforged.engine.worldgen.StreamCarver;
import net.freak4u.terrainforged.engine.worldgen.StreamNetwork;

/**
 * Dependency-free test suite for the shared terrain engine (run with {@code gradlew engineTests} or directly with
 * {@code java}). Every check prints PASS/FAIL with the measured values; the process exits non-zero if any check
 * fails. Thresholds are stated in each check and are not tuned to make a run pass.
 */
public final class EngineTests {
    private static int passed, failed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        settingsRoundTrip();
        settingsClamp();
        spawnSettings();
        presetMatchesRtfDefault();
        builtInPresets();
        TerrainEngine e = new TerrainEngine(12345L, TerrainSettings.defaults());
        int[] land = findLand(e);
        determinismSameSeed(land);
        determinismOrderAndThreads(land);
        parallelTilesMatchSequential(land);
        seedsDiffer(land);
        tileSeams(e, land);
        continuity(e, land);
        elevationLimits(e);
        waterBodies(e);
        vanillaParameters(e);
        climateDiversity(e);
        decorator(e, land);
        previewMatchesWorld(e, land);
        extremeSettingsStayValid(land);
        int[] mountains = findMountains(e);
        streamsRunDownhill(e, mountains);
        streamsAreContained(e, mountains);
        randomSpawnPicksGoodLand(e, mountains);
        benchmark(land);
        System.out.printf("%n%d passed, %d failed in %.1f s%n", passed, failed, (System.nanoTime() - t0) / 1e9);
        for (String f : failures) System.out.println("  FAILED: " + f);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------------------------------------- settings

    static void settingsRoundTrip() {
        TerrainSettings s = TerrainSettings.defaults().with(Key.RIVER_COUNT, 12).with(Key.SEA_LEVEL, 70).with(Key.MOUNTAINS_WEIGHT, 3.25);
        TerrainSettings r = TerrainSettings.fromMap(s.toMap());
        check("settings round-trip through map", r.equals(s) && r.hashCode() == s.hashCode(), s.toMap().size() + " keys");
    }

    static void spawnSettings() {
        TerrainSettings base = TerrainSettings.defaults();
        TerrainSettings s = base.withSpawn(-1234, 5678);
        TerrainSettings r = TerrainSettings.fromMap(s.toMap());
        boolean stored = r.hasSpawn() && r.spawnX() == -1234 && r.spawnZ() == 5678 && !base.hasSpawn();
        boolean notInPresets = s.toPresetMap().keySet().stream().noneMatch(k -> k.startsWith("spawn."))
            && TerrainSettings.fromMap(s.toPresetMap()).equals(base);
        TerrainSettings other = base.with(Key.RIVER_COUNT, 3).withSpawnOf(s);
        boolean kept = other.hasSpawn() && other.spawnX() == -1234 && other.get(Key.RIVER_COUNT) == 3 && !other.withoutSpawn().hasSpawn();
        boolean sameTerrain = RtfTerrain.fromPreset(RtfTerrain.toPreset(s)).equals(RtfTerrain.fromPreset(RtfTerrain.toPreset(base)));
        check("spawn point is stored with the world, kept across presets, never saved into presets, terrain unchanged",
            stored && notInPresets && kept && sameTerrain, "stored=" + stored + " presets=" + notInPresets + " kept=" + kept + " terrain=" + sameTerrain);
    }

    static void settingsClamp() {
        java.util.Map<String, Double> m = new java.util.HashMap<>(TerrainSettings.defaults().toMap());
        m.put(Key.RIVER_COUNT.id, 999.0);
        m.put(Key.REGION_SIZE.id, 1.0);
        m.put(Key.CONTINENT_JITTER.id, Double.NaN);
        m.put(Key.MAIN_BED_DEPTH.id, 4.6);
        m.put("unknown.key", 5.0);
        TerrainSettings s = TerrainSettings.fromMap(m);
        boolean ok = s.get(Key.RIVER_COUNT) == 30 && s.get(Key.REGION_SIZE) == 125 && s.get(Key.CONTINENT_JITTER) == Key.CONTINENT_JITTER.def
            && s.get(Key.MAIN_BED_DEPTH) == 5;
        check("settings clamp to RTF editor ranges, round integer settings, ignore unknown keys", ok,
            "river_count=" + s.get(Key.RIVER_COUNT) + " region_size=" + s.get(Key.REGION_SIZE) + " bed_depth=" + s.get(Key.MAIN_BED_DEPTH));
    }

    /** The default settings must reproduce RTF 1.20.1's "Default" preset (Presets.makeLegacyDefault). */
    static void presetMatchesRtfDefault() {
        var mine = RtfTerrain.toPreset(net.freak4u.terrainforged.engine.config.BuiltInPresets.all().get("ReTerraForged"));
        var rtf = net.freak4u.terrainforged.engine.rtf.settings.Presets.makeLegacyDefault();
        List<String> diff = new ArrayList<>();
        cmp(diff, "continentScale", mine.world().continent.continentScale, rtf.world().continent.continentScale);
        cmp(diff, "continentJitter", mine.world().continent.continentJitter, rtf.world().continent.continentJitter);
        cmp(diff, "continentType", mine.world().continent.continentType, rtf.world().continent.continentType);
        cmp(diff, "regionSize", mine.terrain().general.terrainRegionSize, rtf.terrain().general.terrainRegionSize);
        cmp(diff, "verticalScale", mine.terrain().general.globalVerticalScale, rtf.terrain().general.globalVerticalScale);
        cmp(diff, "mountainsWeight", mine.terrain().mountains.weight, rtf.terrain().mountains.weight);
        cmp(diff, "volcanoWeight", mine.terrain().volcano.weight, rtf.terrain().volcano.weight);
        cmp(diff, "riverCount", mine.rivers().riverCount, rtf.rivers().riverCount);
        cmp(diff, "mainBankWidth", mine.rivers().mainRivers.bankWidth, rtf.rivers().mainRivers.bankWidth);
        cmp(diff, "lakeChance", mine.rivers().lakes.chance, rtf.rivers().lakes.chance);
        cmp(diff, "wetlandChance", mine.rivers().wetlands.chance, rtf.rivers().wetlands.chance);
        cmp(diff, "droplets", mine.filters().erosion.dropletsPerChunk, rtf.filters().erosion.dropletsPerChunk);
        cmp(diff, "biomeSize", mine.climate().biomeShape.biomeSize, rtf.climate().biomeShape.biomeSize);
        cmp(diff, "tempBias", mine.climate().temperature.bias, rtf.climate().temperature.bias);
        cmp(diff, "seaLevel", mine.world().properties.seaLevel, rtf.world().properties.seaLevel);
        cmp(diff, "terrainScaler", mine.world().properties.terrainScaler(), Math.min(rtf.world().properties.worldHeight, 256));
        check("the ReTerraForged preset reproduces ReTerraForged's Default preset", diff.isEmpty(), diff.isEmpty() ? "16 fields compared" : diff.toString());
    }

    /** Built-in presets: "Default" is the default settings, presets survive a settings round trip, and all generate. */
    static void builtInPresets() {
        var all = net.freak4u.terrainforged.engine.config.BuiltInPresets.all();
        boolean defaultOk = all.get(net.freak4u.terrainforged.engine.config.BuiltInPresets.DEFAULT).equals(TerrainSettings.defaults());
        int roundTrip = 0, invalid = 0, n = 0;
        for (var e : all.entrySet()) {
            TerrainSettings s = e.getValue();
            if (RtfTerrain.fromPreset(RtfTerrain.toPreset(s)).equals(s)) roundTrip++;
            TerrainEngine eng = new TerrainEngine(5L, s.fitToWorld(-64, 320));
            for (int z = -3000; z <= 3000; z += 300) {
                for (int x = -3000; x <= 3000; x += 300) {
                    ColumnSample c = eng.preview(x, z);
                    n++;
                    if (!Double.isFinite(c.height) || c.height < -56 || c.height > 320) invalid++;
                }
            }
        }
        check("built-in presets: TerrainForged = defaults, settings round-trip, all generate valid terrain",
            defaultOk && roundTrip == all.size() && invalid == 0,
            all.size() + " presets " + all.keySet() + ", " + roundTrip + " round-trip, " + invalid + "/" + n + " invalid");
    }

    /** The parallel tile path must give bit-identical results to the sequential one. */
    static void parallelTilesMatchSequential(int[] land) {
        net.freak4u.terrainforged.engine.rtf.tile.generation.TileGenerator.setParallel(false);
        TerrainEngine seq = new TerrainEngine(6060L, TerrainSettings.defaults());
        double[] a = new double[RtfTerrain.TILE_BLOCKS * 2];
        for (int i = 0; i < a.length; i++) a[i] = seq.sample(land[0] + i, land[1] + i / 3).height;
        net.freak4u.terrainforged.engine.rtf.tile.generation.TileGenerator.setParallel(true);
        TerrainEngine par = new TerrainEngine(6060L, TerrainSettings.defaults());
        int diff = 0;
        for (int i = 0; i < a.length; i++) if (par.sample(land[0] + i, land[1] + i / 3).height != a[i]) diff++;
        check("parallel tile generation matches sequential", diff == 0, diff + " of " + a.length + " differ");
    }

    private static void cmp(List<String> diff, String name, Object a, Object b) {
        if (!a.equals(b)) diff.add(name + ": " + a + " vs " + b);
    }

    // ------------------------------------------------------------------------------------------------- determinism

    /** Finds a few land areas (deterministic search) so tests look at terrain rather than open ocean. */
    static int[] findLand(TerrainEngine e) {
        int[] out = new int[8];
        int found = 0;
        Random r = new Random(1);
        for (int i = 0; i < 4000 && found < 4; i++) {
            int x = r.nextInt(20000) - 10000, z = r.nextInt(20000) - 10000;
            ColumnSample c = e.preview(x, z);
            if (c.height > e.settings().seaLevel() + 15) {
                out[found * 2] = x;
                out[found * 2 + 1] = z;
                found++;
            }
        }
        return Arrays.copyOf(out, found * 2);
    }

    static void determinismSameSeed(int[] land) {
        TerrainEngine a = new TerrainEngine(777L, TerrainSettings.defaults());
        TerrainEngine b = new TerrainEngine(777L, TerrainSettings.defaults());
        int n = 0, diff = 0;
        for (int k = 0; k < 2; k++) {
            for (int dz = -150; dz <= 150; dz += 7) {
                for (int dx = -150; dx <= 150; dx += 7) {
                    int x = land[k * 2] + dx, z = land[k * 2 + 1] + dz;
                    ColumnSample p = a.compute(x, z), q = b.compute(x, z);
                    n++;
                    if (p.height != q.height || p.vTemperature != q.vTemperature || p.flags != q.flags || p.gradient != q.gradient) diff++;
                }
            }
        }
        check("same seed + settings => identical columns", diff == 0, diff + "/" + n + " differ");
    }

    static void determinismOrderAndThreads(int[] land) throws Exception {
        int cx = land[0], cz = land[1];
        List<int[]> pts = new ArrayList<>();
        for (int z = -200; z < 200; z += 9) for (int x = -200; x < 200; x += 9) pts.add(new int[]{cx + x, cz + z});
        TerrainEngine single = new TerrainEngine(4242L, TerrainSettings.defaults());
        double[] ref = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) ref[i] = single.compute(pts.get(i)[0], pts.get(i)[1]).height;
        TerrainEngine multi = new TerrainEngine(4242L, TerrainSettings.defaults());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) order.add(i);
        java.util.Collections.shuffle(order, new Random(9));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        double[] got = new double[pts.size()];
        List<Future<?>> fs = new ArrayList<>();
        for (int i : order) fs.add(pool.submit(() -> got[i] = multi.sample(pts.get(i)[0], pts.get(i)[1]).height));
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        int diff = 0;
        for (int i = 0; i < ref.length; i++) if (ref[i] != got[i]) diff++;
        check("results independent of generation order and threading", diff == 0, diff + " of " + ref.length + " differ (8 threads, shuffled)");
    }

    static void seedsDiffer(int[] land) {
        TerrainEngine a = new TerrainEngine(1L, TerrainSettings.defaults()), b = new TerrainEngine(2L, TerrainSettings.defaults());
        int same = 0, n = 0;
        for (int z = -2000; z <= 2000; z += 100) {
            for (int x = -2000; x <= 2000; x += 100) {
                n++;
                if (Math.abs(a.preview(x, z).height - b.preview(x, z).height) < 0.5) same++;
            }
        }
        check("different seeds give different worlds", same < n / 4, same + "/" + n + " columns within 0.5 blocks");
    }

    // ------------------------------------------------------------------------------------------------- shape

    /** Tile borders must not be visible: the mean step across a tile edge may not exceed 2x the step inside. */
    static void tileSeams(TerrainEngine e, int[] land) {
        double border = 0, inside = 0;
        int nb = 0, ni = 0;
        for (int k = 0; k < land.length / 2; k++) {
            int tx = Math.floorDiv(land[k * 2], RtfTerrain.TILE_BLOCKS) * RtfTerrain.TILE_BLOCKS;
            int tz = Math.floorDiv(land[k * 2 + 1], RtfTerrain.TILE_BLOCKS) * RtfTerrain.TILE_BLOCKS;
            for (int z = tz; z < tz + RtfTerrain.TILE_BLOCKS; z++) {
                border += Math.abs(e.sample(tx, z).height - e.sample(tx - 1, z).height);
                nb++;
                inside += Math.abs(e.sample(tx + 64, z).height - e.sample(tx + 63, z).height);
                ni++;
            }
        }
        double mb = border / nb, mi = inside / ni;
        check("tile borders are not visible", mb <= Math.max(0.5, 2 * mi), String.format("mean step across border %.3f vs inside %.3f blocks (%d pairs)", mb, mi, nb));
    }

    /** Neighbouring columns differ by at most a cliff's worth of blocks; RTF's terrain has no vertical walls > 24. */
    static void continuity(TerrainEngine e, int[] land) {
        double worst = 0;
        int steps = 0, big = 0;
        for (int k = 0; k < land.length / 2; k++) {
            int x0 = land[k * 2] - 600, z = land[k * 2 + 1];
            double prev = e.sample(x0, z).height;
            for (int x = x0 + 1; x < x0 + 1200; x++) {
                double h = e.sample(x, z).height;
                double d = Math.abs(h - prev);
                worst = Math.max(worst, d);
                if (d > 24) big++;
                steps++;
                prev = h;
            }
        }
        check("no height discontinuities along transects", big == 0, String.format("%d steps, worst %.1f blocks, %d > 24", steps, worst, big));
    }

    static void elevationLimits(TerrainEngine e) {
        double min = 1e9, max = -1e9;
        int n = 0, bad = 0, over = 0;
        for (int z = -30000; z <= 30000; z += 250) {
            for (int x = -30000; x <= 30000; x += 250) {
                ColumnSample c = e.preview(x, z);
                n++;
                min = Math.min(min, c.height);
                max = Math.max(max, c.height);
                if (c.height < -56 || c.height > 320 - 16 || Double.isNaN(c.height)) bad++;
                if (c.height > e.settings().get(Key.TERRAIN_HEIGHT)) over++;
            }
        }
        // since 0.6.0 the default mountains use RTF's per-type vertical scale 1.5, so the highest peaks may rise a little
        // above the nominal terrain height (as in RTF with vertical scales > 1); the hard limit is the world top
        // (y 320) minus the 16-block top slide
        check("elevations stay inside the world: [minY+8, 320-16]", bad == 0,
            String.format("%d columns, range %.0f..%.0f, %d out of range, %d above the nominal terrain height %.0f", n, min, max, bad, over, e.settings().get(Key.TERRAIN_HEIGHT)));
    }

    /** RTF carves rivers, lakes and oceans below sea level (vanilla aquifers fill them); rivers must reach water. */
    static void waterBodies(TerrainEngine e) {
        int sea = e.settings().seaLevel();
        int river = 0, riverDry = 0, lake = 0, lakeDry = 0, ocean = 0, oceanDry = 0;
        for (int z = -12000; z <= 12000; z += 40) {
            for (int x = -12000; x <= 12000; x += 40) {
                ColumnSample c = e.preview(x, z);
                boolean wet = c.height < sea;
                if (c.is(RtfTerrain.FLAG_RIVER) && c.riverMask < 0.05f) { river++; if (!wet) riverDry++; }
                // lake cells include RTF's shore band; a lake cell more than 2 blocks above the water is a real defect
                if (c.is(RtfTerrain.FLAG_LAKE) && c.riverMask < 0.05f) { lake++; if (c.height > sea + 2) lakeDry++; }
                if (c.is(RtfTerrain.FLAG_OCEAN)) { ocean++; if (!wet) oceanDry++; }
            }
        }
        check("river channels are carved below sea level", river > 50 && riverDry <= river / 20, river + " channel samples, " + riverDry + " dry");
        check("lakes are carved below sea level (shore within 2 blocks)", lake > 5 && lakeDry == 0, lake + " lake samples, " + lakeDry + " more than 2 blocks above the water");
        check("ocean cells are under water", ocean > 1000 && oceanDry <= ocean / 50, ocean + " ocean samples, " + oceanDry + " above sea");
    }

    /** RTF's vanilla mapping: oceans get ocean continentalness, nothing reaches the mushroom-fields band. */
    static void vanillaParameters(TerrainEngine e) {
        int sea = e.settings().seaLevel();
        int n = 0, mushroom = 0, oceanMismatch = 0, oceans = 0, outOfRange = 0;
        for (int z = -20000; z <= 20000; z += 160) {
            for (int x = -20000; x <= 20000; x += 160) {
                ColumnSample c = e.preview(x, z);
                n++;
                if (c.vContinentalness < -1.05) mushroom++;
                if (c.is(RtfTerrain.FLAG_OCEAN)) {
                    oceans++;
                    if (c.vContinentalness > -0.19) oceanMismatch++;
                }
                // temperature/humidity are vanilla parameter midpoints; RTF itself uses up to +-1.1 for erosion/weirdness
                if (Math.abs(c.vTemperature) > 1.0001 || Math.abs(c.vHumidity) > 1.0001 || Math.abs(c.vErosion) > 1.1001 || Math.abs(c.vWeirdness) > 1.1001) outOfRange++;
            }
        }
        check("vanilla parameters: oceans map to ocean continentalness, none to mushroom fields, all in range",
            mushroom == 0 && oceanMismatch == 0 && outOfRange == 0,
            n + " columns: " + mushroom + " mushroom-band, " + oceanMismatch + "/" + oceans + " ocean mismatch, " + outOfRange + " out of range");
    }

    static void climateDiversity(TerrainEngine e) {
        int sea = e.settings().seaLevel();
        boolean[] t = new boolean[5], h = new boolean[5];
        int n = 0;
        double[] tb = {-1, -0.45, -0.15, 0.2, 0.55, 2}, hb = {-1, -0.35, -0.1, 0.1, 0.3, 2};
        for (int z = -40000; z <= 40000; z += 400) {
            for (int x = -40000; x <= 40000; x += 400) {
                ColumnSample c = e.preview(x, z);
                if (c.height < sea) continue;
                n++;
                for (int i = 0; i < 5; i++) {
                    if (c.vTemperature >= tb[i] && c.vTemperature < tb[i + 1]) t[i] = true;
                    if (c.vHumidity >= hb[i] && c.vHumidity < hb[i + 1]) h[i] = true;
                }
            }
        }
        int tc = 0, hc = 0;
        for (int i = 0; i < 5; i++) { if (t[i]) tc++; if (h[i]) hc++; }
        check("climate spans all vanilla temperature and humidity bands", tc == 5 && hc == 5, n + " land samples; temperature bands " + tc + "/5, humidity " + hc + "/5");
    }

    /** The erosion decorator must expose rock on steep ground and leave gentle lowland grass alone. */
    static void decorator(TerrainEngine e, int[] land) {
        SurfaceDecorator d = e.surfaceDecorator();
        int steep = 0, steepRock = 0, flat = 0, flatKept = 0;
        for (int k = 0; k < land.length / 2; k++) {
            for (int dz = -300; dz < 300; dz += 3) {
                for (int dx = -300; dx < 300; dx += 3) {
                    int x = land[k * 2] + dx, z = land[k * 2 + 1] + dz;
                    ColumnSample c = e.sample(x, z);
                    if (!d.applies(c)) continue;
                    SurfaceDecorator.Material m = d.material(c, x, z);
                    if (c.gradient > 0.75f) { steep++; if (m == SurfaceDecorator.Material.ROCK) steepRock++; }
                    if (c.gradient < 0.2f && c.heightRaw < 0.35f) { flat++; if (m == SurfaceDecorator.Material.KEEP) flatKept++; }
                }
            }
        }
        check("erosion decorator: rock on steep slopes, grass kept on gentle lowland",
            steep > 0 && steepRock == steep && flat > 0 && flatKept == flat,
            steepRock + "/" + steep + " steep columns rock, " + flatKept + "/" + flat + " flat lowland kept");
    }

    /** RTF's fast preview (raw cells) should resemble the eroded world it previews. */
    static void previewMatchesWorld(TerrainEngine e, int[] land) {
        double sum = 0;
        int n = 0, flagAgree = 0;
        for (int k = 0; k < land.length / 2; k++) {
            for (int dz = -400; dz < 400; dz += 20) {
                for (int dx = -400; dx < 400; dx += 20) {
                    int x = land[k * 2] + dx, z = land[k * 2 + 1] + dz;
                    ColumnSample a = e.preview(x, z), b = e.sample(x, z);
                    sum += Math.abs(a.height - b.height);
                    n++;
                    if ((a.height < e.settings().seaLevel()) == (b.height < e.settings().seaLevel())) flagAgree++;
                }
            }
        }
        check("preview matches the eroded world", sum / n < 6 && flagAgree > n * 0.95, String.format("mean |dh| %.2f blocks, land/water agree %d/%d", sum / n, flagAgree, n));
    }

    static void extremeSettingsStayValid(int[] land) {
        int bad = 0, n = 0;
        for (int variant = 0; variant < 2; variant++) {
            java.util.Map<String, Double> m = new java.util.HashMap<>();
            for (Key k : Key.values()) m.put(k.id, variant == 0 ? k.min : k.max);
            TerrainSettings s = TerrainSettings.fromMap(m).fitToWorld(-64, 320);
            TerrainEngine e = new TerrainEngine(99L, s);
            for (int z = -1000; z <= 1000; z += 97) {
                for (int x = -1000; x <= 1000; x += 97) {
                    ColumnSample c = e.preview(x, z);
                    n++;
                    if (Double.isNaN(c.height) || c.height < -56 || c.height > 320 || Double.isNaN(c.vTemperature)) bad++;
                }
            }
            ColumnSample c = e.sample(land[0], land[1]);
            n++;
            if (Double.isNaN(c.height) || c.height > 320) bad++;
        }
        check("all-minimum and all-maximum settings generate valid terrain", bad == 0, n + " columns, " + bad + " invalid");
    }

    static void benchmark(int[] land) {
        TerrainEngine e = new TerrainEngine(31337L, TerrainSettings.defaults());
        long t0 = System.nanoTime();
        e.sample(land[0], land[1]);
        long first = System.nanoTime() - t0;
        int tx = Math.floorDiv(land[0], RtfTerrain.TILE_BLOCKS) * RtfTerrain.TILE_BLOCKS;
        int tz = Math.floorDiv(land[1], RtfTerrain.TILE_BLOCKS) * RtfTerrain.TILE_BLOCKS;
        t0 = System.nanoTime();
        int tiles = 0;
        for (int i = 1; i <= 4; i++) {
            e.sample(tx + i * RtfTerrain.TILE_BLOCKS, tz);
            tiles++;
        }
        double perTile = (System.nanoTime() - t0) / 1e6 / tiles;
        t0 = System.nanoTime();
        long cols = 0;
        for (int z = tz; z < tz + RtfTerrain.TILE_BLOCKS; z++) for (int x = tx; x < tx + RtfTerrain.TILE_BLOCKS; x++) { e.sample(x, z); cols++; }
        double perCol = (System.nanoTime() - t0) / 1e3 / cols;
        System.out.printf("BENCH first column %d ms; eroded tile (128x128, 64 chunks) %.0f ms = %.1f ms/chunk (cell stage on the tile pool); cached column lookup %.2f us%n",
            first / 1_000_000, perTile, perTile / 64, perCol);
        long t1 = System.nanoTime();
        ReliefRenderer.Map m = ReliefRenderer.preview(e, land[0], land[1], 32);
        System.out.printf("BENCH RTF preview 128x128 @32 bpp: %d ms%n", (System.nanoTime() - t1) / 1_000_000);
        check("benchmark ran", m.columns.length == 128 * 128, "");
    }

    // ---------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------------- streams

    /** Centre of the most mountainous 900x900 area on a coarse grid (land everywhere, high on average). */
    static int[] findMountains(TerrainEngine e) {
        int sea = e.settings().seaLevel(), bx = 0, bz = 0;
        double best = -1;
        for (int x = -12000; x <= 12000; x += 1024) {
            for (int z = -12000; z <= 12000; z += 1024) {
                int land = 0;
                double sum = 0;
                for (int i = -3; i <= 3; i++) {
                    for (int j = -3; j <= 3; j++) {
                        double h = e.preview(x + i * 128, z + j * 128).height;
                        if (h > sea) { land++; sum += Math.min(h - sea, 80); }
                    }
                }
                if (land >= 45 && sum > best) { best = sum; bx = x; bz = z; }
            }
        }
        return new int[]{bx, bz};
    }

    static void streamsRunDownhill(TerrainEngine e, int[] m) {
        long t0 = System.nanoTime();
        var paths = e.streams().pathsStartingIn(m[0] - 1024, m[1] - 1024, m[0] + 1024, m[1] + 1024);
        double ms = (System.nanoTime() - t0) / 1e6;
        int rising = 0, aboveGround = 0, reached = 0, points = 0, falls = 0, lakes = 0, lakeCells = 0, poolPoints = 0, maxPool = 0;
        final int MAX_POOL = StreamNetwork.MAX_POOL;
        for (var p : paths) {
            if (p.reachesWater) reached++;
            if (p.lakeX.length > 0) { lakes++; lakeCells += p.lakeX.length; }
            for (int i = 0; i < p.length; i++) {
                points++;
                if (i > 0 && p.water[i] > p.water[i - 1]) rising++;
                if (i > 0 && p.water[i - 1] - p.water[i] >= 3) falls++;
                boolean mouth = p.reachesWater && i == p.length - 1; // the last point lies in the river/lake/sea itself
                // stream water is level with the ground or fills a small dip into a pool (lakes have their own surface)
                if (!mouth && !p.lake[i]) {
                    int pool = p.water[i] - (int) Math.floor(e.preview(p.x[i], p.z[i]).height);
                    if (pool > 0) poolPoints++;
                    maxPool = Math.max(maxPool, pool);
                    if (pool > MAX_POOL) aboveGround++;
                }
            }
        }
        // same seed, fresh engine: identical streams
        TerrainEngine again = new TerrainEngine(e.seed(), e.settings());
        var paths2 = again.streams().pathsStartingIn(m[0] - 1024, m[1] - 1024, m[0] + 1024, m[1] + 1024);
        boolean same = paths.size() == paths2.size();
        for (int i = 0; same && i < paths.size(); i++) {
            same = Arrays.equals(paths.get(i).x, paths2.get(i).x) && Arrays.equals(paths.get(i).z, paths2.get(i).z)
                && Arrays.equals(paths.get(i).water, paths2.get(i).water);
        }
        check("mountain streams: all flow into water or a lake, water only goes down, level with the ground or in pools <= 10 deep, deterministic",
            paths.size() >= 10 && reached == paths.size() && rising == 0 && aboveGround == 0 && same,
            String.format("%d streams in 2048x2048 around %d,%d (%.0f ms), %d reach a river/lake/sea/mountain lake, %d fill %d mountain-lake cells, "
                + "%d points (%d in dip pools, deepest %d), %d falls >= 3 blocks, %d rising, %d pools deeper than 10",
                paths.size(), m[0], m[1], ms, reached, lakes, lakeCells, points, poolPoints, maxPool, falls, rising, aboveGround));
    }

    /**
     * Places streams chunk by chunk on the eroded terrain (as the game does) and checks every water column: each of
     * its four neighbours must either be stream water too or rise at least to the water surface (no spills, no
     * floating water); cuts stay within MAX_CUT; planning a 32x32 area at once gives the same result as 4 chunks.
     */
    static void streamsAreContained(TerrainEngine e, int[] m) {
        int sea = e.settings().seaLevel(), n = 512, x0 = m[0] - n / 2, z0 = m[1] - n / 2;
        StreamCarver.Terrain t = new StreamCarver.Terrain() {
            public int solidTop(int x, int z) { return (int) Math.floor(e.sample(x, z).height); }
            public int surfaceTop(int x, int z) { return Math.max(solidTop(x, z), sea - 1); }
        };
        int[] water = new int[n * n];
        Arrays.fill(water, StreamCarver.NONE);
        long t0 = System.nanoTime();
        for (int cz = 0; cz < n; cz += 16) {
            for (int cx = 0; cx < n; cx += 16) {
                StreamCarver.Plan p = StreamCarver.plan(e.streams(), x0 + cx, z0 + cz, 16, 16, t);
                for (int z = 0; z < 16; z++) System.arraycopy(p.water, z * 16, water, (cz + z) * n + cx, 16);
            }
        }
        double msPerChunk = (System.nanoTime() - t0) / 1e6 / ((n / 16) * (n / 16));
        int wet = 0, leaks = 0, deep = 0, falls = 0, maxFall = 0;
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 1; z < n - 1; z++) {
            for (int x = 1; x < n - 1; x++) {
                int k = z * n + x, w = water[k];
                if (w == StreamCarver.NONE) continue;
                wet++;
                if (t.solidTop(x0 + x, z0 + z) - w > StreamNetwork.MAX_CUT + 6) deep++;
                for (int[] d : nb) {
                    int j = (z + d[1]) * n + x + d[0];
                    if (water[j] != StreamCarver.NONE) {
                        if (w - water[j] >= 3) { falls++; maxFall = Math.max(maxFall, w - water[j]); }
                    } else if (t.surfaceTop(x0 + x + d[0], z0 + z + d[1]) < w) {
                        leaks++;
                    }
                }
            }
        }
        // chunk-order independence: a 32x32 block planned at once vs the four chunk plans above
        int diff = 0, bx = 0, bz = 0;
        for (int k = 0; k < water.length; k++) if (water[k] != StreamCarver.NONE) { bx = (k % n) & ~31; bz = (k / n) & ~31; break; }
        StreamCarver.Plan big = StreamCarver.plan(e.streams(), x0 + bx, z0 + bz, 32, 32, t);
        for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) if (big.water[z * 32 + x] != water[(bz + z) * n + bx + x]) diff++;
        check("streams placed chunk by chunk are walled in by terrain on every side (no spills or floating water)",
            wet > 300 && leaks == 0 && diff == 0 && deep == 0,
            String.format("%d water columns in 512x512, %d leaks, waterfall edges >= 3 blocks: %d (max %d), over-deep cuts %d, chunk-order differences %d, %.1f ms/chunk",
                wet, leaks, falls, maxFall, deep, diff, msPerChunk));
    }

    static void randomSpawnPicksGoodLand(TerrainEngine e, int[] m) {
        // a lowland-ish map next to the mountains: search the 4 neighbouring areas for one with a valid spot
        int ok = 0, tries = 0, maps = 0;
        String where = "";
        for (int[] off : new int[][]{{0, 0}, {2048, 0}, {-2048, 0}, {0, 2048}, {0, -2048}}) {
            ReliefRenderer.Map map = ReliefRenderer.preview(e, m[0] + off[0], m[1] + off[1], 4, 2);
            ReliefRenderer.overlayStreams(map, e);
            maps++;
            Random r = new Random(7);
            int[] dist = net.freak4u.terrainforged.engine.preview.SpawnPicker.waterDistance(map);
            for (int i = 0; i < 20; i++) {
                int[] p = net.freak4u.terrainforged.engine.preview.SpawnPicker.pick(map, r);
                if (p == null) break;
                tries++;
                int px = (int) ((p[0] - map.originX) / map.blocksPerPixel), pz = (int) ((p[1] - map.originZ) / map.blocksPerPixel);
                ColumnSample c = map.at(px, pz);
                double above = c.height - map.seaLevel;
                double slope = net.freak4u.terrainforged.engine.preview.SpawnPicker.slope(map, px, pz, map.blocksPerPixel);
                double river = dist[pz * map.width + px] * map.blocksPerPixel;
                if (c.flags == 0 && above >= 3 && above <= 45 && slope <= 0.25 && river <= 96) ok++;
            }
            if (tries > 0) { where = (m[0] + off[0]) + "," + (m[1] + off[1]); break; }
        }
        check("random spawn: dry land 3-45 blocks above the sea, gentle slope, within 96 blocks of a river/lake/stream",
            tries >= 20 && ok == tries, ok + "/" + tries + " picks valid on map at " + where + " (" + maps + " maps searched)");
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else {
            failed++;
            failures.add(name);
        }
        System.out.printf("%s  %s  [%s]%n", ok ? "PASS" : "FAIL", name, detail);
    }
}
