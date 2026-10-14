# TerrainForged — Engineering Report (Phases 1–13, v0.7.0)

Made by Freak4U. This document covers the Phase 1 reverse-engineering of TerraForged and ReTerraForged, the
toolchain verification for Minecraft 1.21.11, and the architecture and validation of what has been built.

## 1. Reference repositories inspected

| Project | Revision | MC / loader | Licence |
|---|---|---|---|
| TerraForged/TerraForged | `6dd607e` (2022-06-16) | 1.19, Forge 41 (+ Fabric module) | MIT |
| TerraForged/Engine (noise library TF depends on) | git submodule; **no longer publicly reachable** (GitHub asks for credentials) | — | — |
| racoonman2/reterraforged, branch `1.20.1` | `29239f1` (2024-01-09, "Port of RTF 0.0.6 to 1.20.1") | 1.20.1, Forge/Fabric (+TerraBlender) | MIT |
| racoonman2/reterraforged, branch `1.20.2` | `98fd3a2` (2024-02-21) | 1.20.2 — an unfinished rework (e.g. a debug line overrides the terrain with plateaus; no river map in the heightmap) | MIT |

Because the TF Engine repository is gone, ReTerraForged — which vendored a port of that engine — is the primary
algorithmic reference. v0.2 ported the default (`1.20.2`) branch; review against the screenshots showed it is a
work in progress, so v0.3 ports the released `1.20.1` branch instead. Paths in 1.1 are from the 1.20.2 layout; the
1.20.1 equivalents live under `world/worldgen/cell/**` and `world/worldgen/densityfunction/tile/**`.

### 1.1 How TerraForged / ReTerraForged generate terrain

Source files (RTF paths under `common/src/main/java/raccoonman/reterraforged/world/worldgen/`):

| Stage | Files | How it works |
|---|---|---|
| Continents | `continent/advanced/AdvancedContinentGenerator.java`, `AbstractContinent.java` | Domain-warped jittered Voronoi; the "continent value" is the distance to the nearest perpendicular bisector between the cell point and a neighbour, so plate boundaries are always ocean. Per-cell size variance, cell skipping, cliff/bay noise near coasts. |
| Terrain regions | `terrain/region/RegionModule.java`, `RegionSelector.java`, `RegionLerper.java`, `terrain/populator/Populators.java` | A second Voronoi picks a terrain *type* per region (plains, steppe, hills 1/2, dales, plateau, badlands, mountains 1–3, volcano…); each type is a hand-tuned noise recipe; region borders are lerped. |
| Mountain chains | `heightmap/Heightmap.java` (`mountainChainAlpha`), `terrain/MountainChainPopulator.java` | Warped Worley-edge noise at scale 1000 → bands along cell edges → connected chains blended over the regional terrain. |
| Rivers | `continent/simple/SimpleRiverGenerator.java`, `rivermap/river/BaseRiverGenerator.java`, `Network.java`, `terrain/populator/RiverPopulator.java` | **Geometric**: per continent, *N* straight river lines radiate from the continent centre toward the coast (binary search for the ocean), recursive forks at fixed angles, lakes at river heads, wetlands along rivers. Lines are domain-warped for meanders. The carver lowers terrain toward a bed height derived from `levels.water` → every river surface is at sea level. |
| Erosion | `tile/filter/Erosion.java`, `WorldErosion.java`, `Smoothing.java` | Droplet hydraulic erosion (Lague-style brushes) on 2D *tiles* of cells, seeded per chunk, plus smoothing; tiles have borders to hide seams. |
| Climate/biomes | `climate/Climate.java`, `BiomeNoise.java`, `heightmap/Heightmap.applyClimate/applyPost` | Cell fields mapped onto vanilla continentalness/erosion/weirdness; TerraBlender compat. |
| Integration | `densityfunction/CellSampler.java`, `mixin/MixinRandomState.java`, `MixinNoiseBasedChunkGenerator.java`, `MixinNoiseChunk.java`, `MixinChunkStatus.java`, … | Custom density-function *markers* in the router are swapped by a `RandomState` mixin for samplers bound to a `GeneratorContext` (seed + preset + tile cache). More mixins cull noise above the highest point, change chunk status behaviour, structures, surface system and spawn finding. |
| Caching / threads | `tile/TileCache.java`, `concurrent/*` | Tiles generated in batches on a world-gen thread pool and cached with expiry. |

### 1.2 Assessment

* **Strong**: plate continents (always ocean-bounded), Worley-edge mountain chains, rich per-terrain-type noise
  recipes, and the idea of feeding the vanilla router through density functions.
* **Limits**:
  * Rivers are geometric (lines from the continent centre toward the coast) and all sit at sea level, so there are
    no mountain streams or waterfalls. v0.1/v0.2 tried to improve on this with a separate drainage model; in game
    the two height fields disagreed (floating lakes, raised banks), so v0.3 keeps RTF's carved rivers, which are
    consistent with the terrain by construction.
  * Heavy reliance on mixins into `RandomState`, `NoiseBasedChunkGenerator`, `NoiseChunk`, `ChunkStatus`…
    In 1.21.11 `NoiseBasedChunkGenerator` is **final** and these internals changed (e.g. `ResourceLocation` →
    `Identifier`), so the integration cannot be ported as-is.

## 2. Toolchain verification for Minecraft 1.21.11

| Item | Verified value | How |
|---|---|---|
| Fabric | Loom `fabric-loom` 1.17-SNAPSHOT (v0.2.1; 1.18 needs Java 25 to run Gradle — failed on the user's Java 21 setup), Loader 0.19.5, Fabric API 0.141.6+1.21.11, `loom.officialMojangMappings()` | `FabricMC/fabric-example-mod` branch `1.21.11` |
| Forge | **Forge 1.21.11 exists** (61.1.x at first check; v0.2 targets `[61.2,)`, `forge_version=61.2.3` configured (the version in the user's log; not built here)), ForgeGradle 7 (`[7.0.3,8)`), `mappings channel: 'official'`, EventBus 7 (`BusGroup`, `FMLJavaModLoadingContext#getModBusGroup`) | `MinecraftForge/MinecraftForge` branch `1.21.11` (MDK + sources), Forge maven changelog |
| Java | 21 (both loaders) | example mod / MDK |
| Gradle | 9.7.1 wrapper (Fabric example); Forge's own repo uses 9.5.0 | wrapper properties |
| Vanilla APIs | `RandomState`, `NoiseRouter` (incl. `preliminarySurfaceLevel`), `DensityFunction.SimpleFunction`, `NoiseHolder`, `KeyDispatchDataCodec`, `Registries.DENSITY_FUNCTION_TYPE`, `Feature`, `ServerChunkCache.randomState()` | `javap` against the 1.21.11 Mojang-named jar from the local Loom cache |
| Vanilla data | overworld noise settings, world presets, dimension type | `misode/mcmeta` tag `1.21.11-data-json` |

No NeoForge substitution was made: Forge is a real, current target for 1.21.11.

## 3. TerrainForged architecture

```
common/      pure-Java engine (no Minecraft imports) + dependency-free test suite
common-mc/   shared Minecraft integration (Mojang names) + generated data pack resources
fabric/      Fabric entry point + build (compiles common + common-mc into the jar)
forge/       Forge entry point, mods.toml, biome modifier + build (same shared sources)
tools/       preset generator (vanilla router -> TerrainForged router)
```

Pipeline (engine `TerrainEngine`, v0.3):

1. **ReTerraForged 1.20.1 heightmap** (`engine/rtf/**`, released "RTF 0.0.6" branch, see
   `THIRD_PARTY_NOTICES.md`): advanced continents (warped jittered Voronoi, ocean-bounded plates), terrain regions
   with a blended pair-wise mix of steppe/plains/hills/dales/plateau/badlands/torridonian and unmixed
   mountains 1–3 and volcanoes, mountain chains, deep/shallow ocean and coast populators.
2. **River map** (`cell/rivermap/**`): per continent, main rivers and branches from RTF's `BaseRiverGenerator`,
   warped for meanders, with lakes at river heads and wetlands along rivers; each is carved into the heightmap
   with bed/bank profiles down to below sea level.
3. **Climate** (`cell/climate/**`): RTF's temperature/moisture range noises, biome regions with warped edges, and
   Whittaker-style biome types → vanilla temperature/humidity parameter values; river valleys, rivers, lakes and
   wetlands set erosion/weirdness so vanilla picks river/swamp biomes there.
4. **Tiles** (`RtfTerrain`): 128×128-block tiles with a one-chunk border run RTF's filters — droplet erosion,
   smoothing, steepness (gradient) and beach detection; finished tiles are reduced to compact arrays (LRU of 192,
   one generating thread per tile, others wait on its future). 0.5 s per tile = ≈8 ms per chunk on one thread.
5. **Vanilla parameters**: continentalness exactly as RTF's `CellSampler.Field.CONTINENT`; erosion, weirdness,
   temperature and humidity are RTF's cell values.
6. **Minecraft integration**: the vanilla router is kept; terrain and climate inputs come from
   `terrainforged:engine` density functions (`tools/generate_presets.py`). Rivers, lakes and oceans are carved below
   sea level and filled by vanilla's aquifers, as in RTF. For that to work, the router's `preliminary_surface_level`
   (which vanilla only uses for aquifers and the surface rules' "above preliminary surface" gate) reports the
   terrain 16 blocks lower than it is — RTF does the equivalent by lowering its initial density by 90/128 units — so
   aquifer grid points on river banks also pick the sea-level fluid. Caves, ores, structures and mob spawning are
   vanilla.
7. **Decorators** (`ErodeFeature` in `raw_generation`, `DecorateSnowFeature` in `top_layer_modification`, logic in
   `SurfaceDecorator`): TerraForged's erosion decorator (stone on steep/high ground, coarse dirt/gravel on moderate
   slopes, gravel scree from droplet sediment) and natural snow (snow slides off steep slopes; snow layer heights
   follow the exact terrain height), with RTF's default configuration values.
8. **World-creation UI** (`TerrainForgedSettingsScreen`): all 109 settings as sliders on seven pages (world,
   climate, terrain, terrain types, rivers & lakes, erosion, decoration), RTF's preview path (one 128×128 tile of
   raw cells spread N blocks apart) for the exact engine seed of the typed world seed, pan/zoom, Reset, Save as
   Default. *Done* rebuilds the overworld `NoiseGeneratorSettings` with every engine function re-bound to the new
   settings (and vanilla `sea_level` set to match), so the choice is encoded into level.dat. Hook: Forge
   `RegisterPresetEditorsEvent`; Fabric uses one client-only `@Inject` on `WorldCreationUiState#getPresetEditor`.

### Why v0.3 replaced v0.2's water model

v0.2 ran a home-grown continent-wide drainage solver (64-block grid, priority flood, stream-power incision) on top
of RTF's eroded tiles and placed water above sea level with a block feature. The two height fields disagreed in
places, so the feature raised banks to keep water in: in game this produced lakes standing above flat land with
blocky sand walls (user screenshot, v0.2.1). v0.3 drops that layer and uses RTF's own river map, which carves into
the same heightmap the tiles erode — no second height field, no water above sea level, no raised banks.

### Differences from ReTerraForged

| Concern | ReTerraForged 1.20.1 | TerrainForged 0.3 |
|---|---|---|
| Terrain, rivers, climate, filters | — | same algorithms and default values (ported) |
| Integration | mixins into `RandomState`, `NoiseChunk`, `ChunkStatus`, … (internals changed or became final in 1.21.11) | worldgen without mixins: a registered density-function type, two features, data-pack presets; one client-only Fabric mixin for the Customize button |
| World seed | `RandomState` mixin | a registered noise instantiated by `RandomState#visitNoise`, hashed into the engine seed |
| Settings persistence | preset registry/data pack | settings serialised inside the world's generator (level.dat); config only affects new worlds |
| Terrain scale | capped at 256 blocks | 256 by default; the Tall preset scales terrain to 900 blocks in a 2048-high world |
| Tile cache | expiring concurrent cache, batch thread pool | bounded LRU, one generating thread per tile |

### Seed & settings flow (no mixins)

`world_preset` (inline noise settings) → `terrainforged:engine{settings:{from_config:1}}` → `SettingsCodec`
reads `config/terrainforged.json` once at world creation (or the Customize screen replaces the generator) → world
saved → level.dat contains the concrete numbers. `RandomState` wiring calls `mapAll` → `visitNoise(seed_probe)` →
hashed seed → shared `TerrainEngine` from `EngineRegistry`.

### Caching and threading

* `RtfTerrain`: LRU of 192 eroded tiles (128×128 blocks, ≈0.4 MB each). Since 0.4.0 the cell stage of a tile (≈75 %
  of its cost) runs row by row on a shared pool `TerrainForged-Tile-*` (half the cores, at least 2); the filters
  (droplet erosion, smoothing) stay on the requesting thread. Tested bit-identical to the sequential path. On the
  2-core build sandbox a tile went from ≈430 ms to ≈340–390 ms; more cores help more.
* `RiverCache`: LRU of 64 river maps (one per continent).
* Per-thread direct-mapped column cache (4096 entries) — the router queries several fields per column.
* Engine code is thread-safe and deterministic (tested bit-for-bit, 8 threads, shuffled order).

## 4. Extended height (stretch goal)

Minecraft 1.21.11 supports `min_y >= -2032`, `min_y + height <= 2032` (`DimensionType.MIN_Y/MAX_Y`), height a
multiple of 16. The **TerrainForged (Tall 2048)** preset uses its own dimension type with `min_y -64`,
`height 2048` (build limit y = 1983), noise settings with the same range, vanilla slides moved to the new limits,
and separate defaults (`config/terrainforged-tall.json`: `terrain_height` 900, `vertical_scale` 0.9, `horizontal_scale` 1.6, `continent_scale` 4500, snowline 260).

* Heightmaps, sections, lighting and serialisation are vanilla's — nothing is hard-coded.
* Cost: the noise stage scales with height; vanilla fills every 8-block cell of the column, so noise filling is
  roughly 5× the standard preset per chunk (the headless test measured 15.8 s vs 8.9 s for 2 000 full base
  columns). RTF culled noise above the terrain with a mixin; TerrainForged does not (by design: no mixins),
  so the tall preset is opt-in and **not** the default.
* Not yet verified in-game: lighting performance, structure placement at extreme heights, client rendering.

## 5. Validation performed

**v0.7.0 re-run:** `EngineTests` 24/24 PASS; `HeadlessWorldgenTest` quick (standard) 18/18 PASS. Stream checks now:
109 streams in 2048×2048 blocks of mountains, all 109 end in a river/lake/sea or mountain lake (15 fill mountain
lakes, 7 314 lake blocks), 0 rising, dip pools at most 10 deep (398 of 12 044 points in pools); placement on a
512×512 area chunk by chunk: 1 149 water columns, 0 leaks, 21 drops ≥ 3 (max 5), chunk-order independent; on real
generated blocks: 165 water columns, 0 sides open to land or caves. Fullness (separate measurement, two 512×512
areas): the lower bank is level with the water at 76 % / 71 % of stream edge columns and within 1 block at 88 % / 96 %
(0.6.0 in the same areas: level with the lower bank at 6 % / 3 %, 5 or more blocks below it at 26 % / 10 %). Changed check
(disclosed): "never above the ground" became "level with the ground or in a dip pool ≤ 10 deep", because 0.7 fills
dips on purpose. Cost: a cold chunk in dense mountains traces nearby streams for ~3.6 s single-threaded (shared by
the chunk-generation threads, computed once per area); lowland chunks ~0–1 s.

**v0.6.0 re-run:** `EngineTests` 24/24 PASS. New: mountain streams — 108 streams traced in 2048×2048 blocks of
mountains (3.7 s, 24 reach a river/lake/sea, 952 drops ≥ 3 blocks), water level never rises along a path and never
lies above the ground (mouth points in the receiving water excepted), same seed → identical paths; stream placement
chunk by chunk on the eroded terrain of a 512×512 area — 1 224 water columns, 0 leaks (every neighbour is stream water
or rises to the water surface), 55 waterfall edges up to 7 blocks, 0 over-deep cuts, a 32×32 plan identical to its
four chunk plans; random spawn — 20/20 picks valid. Changed checks (disclosed): "Default = defaults" is now
"TerrainForged = defaults" and the RTF-default comparison uses the "ReTerraForged" preset (the default changed on
purpose); the elevation check now bounds heights by the world (y ≤ 320−16) instead of the nominal terrain height,
because the new default's mountain vertical scale 1.5 lets 4 of 58 081 sampled columns rise above 256 (max 284), as
RTF does for vertical scales above 1. `HeadlessWorldgenTest` quick, standard preset 18/18 PASS; new: pre-0.6 world
settings decode with streams off; streams planned from real generated blocks (vanilla noise, caves, aquifers) over
3×3 chunks and applied — 109 water columns, 164 water blocks, 0 sides open to land, 0 into caves, 14 pouring into
lower stream columns. Tall preset quick run 18/18 PASS (stream check: 138 water columns, 276 water blocks, 0 open
sides, 72 pouring into lower stream columns). Not executed: `StreamFeature` inside a running game (block writes, fluid ticks, heightmaps).

**v0.5.0 re-run:** `EngineTests` 21/21 PASS (adds: the spawn point is stored with the world, kept when switching
presets, never written to presets/config defaults, and does not change the terrain). `HeadlessWorldgenTest`
(standard preset, quick mode) 17/17 PASS (adds: the chosen spawn survives the level.dat round trip; a spawn picked
in water moves to the nearest dry land — e.g. −960,−160 → −904,−152 — and a dry pick is kept exactly). Engine, MC
code, client screen and both loader entry points compile against the 1.21.11 jars; the two new loader hooks
(Fabric `ServerLifecycleEvents.SERVER_STARTED`, Forge `ServerStartedEvent.BUS`) were checked against the Fabric API
`1.21.11` and Forge `1.21.11` branch sources and compiled against stubs with those exact signatures. Not executed:
`SpawnPlacer.place` on a running server (needs the game).

**v0.4.0 re-run:** `EngineTests` 20/20 PASS (adds: built-in presets — Default equals the defaults, all 7 presets
round-trip through `toPreset`/`fromPreset` and generate valid terrain; parallel tile generation bit-identical to
sequential). `HeadlessWorldgenTest` (standard preset, quick mode) 15/15 PASS. Engine + MC + client screen + both
loader entry points compile against the 1.21.11 jars. The v0.3.0 details follow.

### v0.3.0

All results below were executed in the build sandbox; nothing is claimed that was not run.

* `EngineTests` — **18 checks, all PASS** (≈140 s): settings round-trip and clamping to RTF's editor ranges
  (109 keys); default settings reproduce RTF's "Default" preset (16 fields compared against the ported
  `Presets.makeLegacyDefault`); determinism (same seed; 8 threads in shuffled order, bit-for-bit); seeds differ;
  tile borders invisible (mean step across a tile border 0.18 vs 0.22 blocks inside); no discontinuities > 24 blocks
  (worst 2.1 over 4 796 steps); elevations within limits (6..218 over 58 081 columns); river channels carved below
  sea level (1 485 samples, 0 dry); lakes below sea level apart from a ≤ 2-block shore band; ocean cells under water;
  vanilla parameters (0 mushroom-band continentalness, oceans map to ocean continentalness, temperature/humidity in
  [-1, 1], erosion/weirdness within RTF's ±1.1); all vanilla temperature/humidity bands present on land; erosion
  decorator puts rock on every steep column and keeps flat lowland grass; RTF preview vs eroded world mean |Δh|
  0.62 blocks; all-min/all-max settings valid; benchmark ≈0.5 s per eroded tile = ≈8 ms per chunk single-threaded,
  RTF preview 128×128 in ≈0.3–0.4 s.
  Two checks were refined while bringing them up for v0.3, and are reported here: the lake check allows RTF's shore
  band (lake cells up to 2 blocks above the water; 23 of 219 lake samples sit in that band), and the parameter-range
  check allows RTF's own ±1.1 for erosion/weirdness (RTF assigns 1.1 to ocean cells).
* `HeadlessWorldgenTest` (real 1.21.11 classes, vanilla registries bootstrapped): settings encode to concrete
  values; Customize-screen path (settings swapped in all 10 engine functions, level.dat round trip incl. vanilla
  sea level); engine wiring via `RandomState`; generated surface vs engine height (mean |Δy| ≈ 0.9, p90 ≈ 1.1);
  oceans filled; caves present; **river and lake channels filled with water by the vanilla aquifers** (149 of 150
  carved channel columns hold water up to sea level — this check failed at first with 26/150, which is how the
  aquifer-surface bias described in section 3 was found and verified); no mushroom fields; beaches present.
  Runs: standard preset with 2 000 surface columns — 15/15 PASS (31 surface biomes); after adding 12 biome-sample
  patches on high ground (the tall run had first failed the "mountain biomes present" check because its fixed sample
  patches happened to miss mountains — 7.2 % of tall-preset land has peak-range erosion vs 6.4 % standard, measured
  separately), quick runs (300 surface columns) of both presets — 15/15 PASS each (standard 42 biomes, tall 39;
  tall surface mean |Δy| 1.15, p90 1.1).
* Compile checks: engine + shared MC code + client screen against the real 1.21.11 Mojang-named client jar; the
  Fabric mixin against sponge-mixin 0.17.4; the Forge client hook against EventBus 7.0.5 and fmlloader jars.

**Not executed** (sandbox has no access to the Fabric/Forge/Mojang Maven repositories): Gradle builds of the
loader jars, game launches, the erosion/snow decorators on real chunks (their decision logic is unit-tested; block
placement is compile-checked only), the Customize screen in a running client.

### Phase 10 (v0.4.0)

* Presets: `BuiltInPresets` converts ReTerraForged's `Presets.makeLegacy{Default, Beautiful, HugeBiomes, Lite,
  Vanillaish}` with `RtfTerrain.fromPreset` (the inverse of `toPreset`; tested to round-trip), plus Archipelago and
  Alpine. RTF's `makeLegacy1_18` throws "TODO" in RTF itself and is not offered. User presets are flat JSON maps in
  `config/terrainforged/presets/<name>.json` (`TerrainForgedConfig`). Two keys were added so RTF presets reproduce
  exactly: `world.continent_type` and `climate.biome_edge.type`.
* Preview: `ReliefRenderer.preview` stitches 2×2 RTF preview tiles (256×256); modes Relief (hypsometric tint + NW
  hill shading), Climate (temperature/humidity colours), Contours (16-block contour lines).
* Snow polish: `SurfaceDecorator.snowyMaterial` / `snowErodes` keep only RTF's steepest rule on snow-covered ground.
* Voxy: client-side LOD renderer that builds LODs from generated/loaded chunks, so no integration code is needed;
  in-game check listed in `docs/RELEASE.md`.

### Phase 11 (v0.5.0): spawn point

* `TerrainSettings` gained world-only keys `spawn.enabled`, `spawn.x`, `spawn.z` (`Key.isWorldOnly()`): no slider,
  excluded from presets and config writes (`toPresetMap()`), ignored by the terrain (the RTF preset is unchanged).
* Customize screen: Set Spawn / Clear Spawn button, right-click or pick-mode click on the map, yellow pin marker,
  spawn line under the map, water warning.
* `SpawnPlacer` (common-mc): on server start, if the world's generator has a spawn and the world folder has no
  `terrainforged_spawn_placed.txt`, it finds dry land (ring search, 8-block steps, up to 128 blocks, using the
  engine's eroded columns), generates that chunk, takes the `MOTION_BLOCKING_NO_LEAVES` height, calls
  `MinecraftServer.setRespawnData`, and writes the marker so restarts and `/setworldspawn` are respected. It runs
  before any player can log in (logins are handled in server ticks).
* Limitation: vanilla has already chosen (and generated) its own spawn area by then, and the bonus chest, if
  enabled, is placed at vanilla's spawn.

### Phase 12 (v0.6.0): mountain streams, waterfalls, new default, random spawn

* `StreamNetwork` (engine): one possible source per 64×64 cell (chance × altitude × moisture factor, at least
  `min_altitude` above the sea); steepest descent over RTF's raw (un-eroded) height at 2-block steps; closed hollows
  are crossed by a straight notch to the nearest lower point within 48 blocks if the cut stays ≤ 10 blocks, otherwise
  the stream ends in a pool (dry-ended paths shorter than 80 blocks are dropped). Water level = running minimum of
  ground − 1, then "terraced": runs whose level falls ≤ one step (1–2 blocks on gentle ground, 3–7 on slopes steeper
  than 0.25) are flattened to their downstream level — pools with a drop at each boundary. Width 1/2/3 blocks with
  distance; paths cached per cell (one computation per cell across threads).
* `StreamCarver` (engine, pure): per column `min(level, top−1)`, lowered to the surface (incl. fluids) of every
  neighbour that holds no stream water; columns needing a cut > 10 stay dry and count as walls. Depends only on terrain
  within 2 blocks, so chunks agree regardless of generation order.
* `StreamFeature` (decoration step `lakes`, before trees): reads block heights, writes air / water sources / gravel
  bed (stone where unsupported), schedules a fluid tick on every column's top water block and on stream water just
  across the chunk edge so drops start flowing.
* Old worlds: a saved settings map without `rivers.streams.chance` decodes with chance 0 (`SettingsCodec`).
* Defaults: TerrainForged's own (based on RTF "Beautiful", see README); `BuiltInPresets` lists TerrainForged first and
  RTF's default as "ReTerraForged". `TerrainForgedConfig` renames config files that older versions wrote automatically
  (recognised by their readme text, no `_defaults_version`) to `*.json.old` and writes the new defaults.
* `SpawnPicker` (engine): weighted random among preview pixels that are dry land 3–45 blocks above the sea, slope ≤
  0.25, 4–96 blocks from river/lake/stream pixels (breadth-first distance), temperate climates preferred.

### Phase 13 (v0.7.0): stream rework

* Tracing: steepest descent over a smoothed raw height (0.4·h + 0.15·Σh(±4 blocks)) on the even grid; visited cells are
  excluded. Hollows: priority flood (≤ 900 cells of 2×2 blocks) — the first cell below the lake surface beyond the rim
  is the outlet; route back via parents; lake surface = min(spill−1, upstream water, lowest block next to the lake
  − 1), lake blocks = blocks below the surface (edges checked per block). No outlet within the limit → the stream ends
  in that lake. Streams not ending in water or a lake are dropped; coastal flats (≤ sea+3) count as arrival.
* Water profile: from the mouth upward `w = max(ground, min(w_downstream, ground + 10))`, then a forward minimum (lake
  outlets notched to the lake), then terracing (1 on gentle ground, 2–3 on steep).
* Carver: the `top − 1` rule is gone (water may be level with or above its own ground; containment by non-stream
  neighbours is unchanged); bed = min(level − depth, ground).
* Shared raw-height cache (128×128-block tiles, lazily filled) for all traces.

## 6. Known limitations / next steps

* Rivers and lakes sit at sea level as in ReTerraForged; above sea level there are only the 0.6 mountain streams
  (and their end pools). About a quarter of the streams reach a river, lake or the sea; the rest end in a small pool in
  a mountain hollow. Streams are not yet verified in a running game.
* Structures placed after the `lakes` step (villages, outposts) may sit across a stream channel.
* The aquifer-surface bias (16 blocks) also floods air pockets just below sea level near the surface more often
  than vanilla (RTF behaves the same way).
* Tile erosion costs ≈0.5 s per 128-block tile on one thread (≈8 ms per chunk), the same work RTF does; the first
  chunks of a new area are slower.
* Worlds created with 0.2.x or earlier generate different terrain in new chunks.
* Modded biome compatibility relies on mods that extend the vanilla overworld multi-noise parameter list; not
  tested with specific mods.
* Tall preset: RTF's terrain stretched to a 900-block range; not yet verified in game.
