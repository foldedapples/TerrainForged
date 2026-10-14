# TerrainForged — release & in-game checklist (Phase 10)

Made by Freak4U. Everything here needs the real game or your CurseForge/Modrinth accounts, so it is done by you;
the build sandbox cannot launch Minecraft or log in to mod sites.

## 1. Build

```bat
gradlew engineTests
gradlew -p fabric build      :: fabric\build\libs\terrainforged-0.7.0+fabric-1.21.11.jar
gradlew -p forge build       :: forge\build\libs\terrainforged-0.7.0+forge-1.21.11.jar
```

Upload the jars **without** `-sources` in the name.

## 2. In-game checks before release

Fabric and Forge each:

- [ ] Mod list shows the TerrainForged icon and description.
- [ ] Create World → More → World Type: TerrainForged → Customize opens.
  - [ ] Preset `<` / `>` switches presets and the preview updates; Save Preset with a name, then it appears with "(yours)";
        Delete removes it (files live in `config/terrainforged/presets`).
  - [ ] Scroll over the map zooms, dragging moves it, View cycles Relief / Climate / Contours, the line under the map
        shows coordinates and height of the point under the cursor.
  - [ ] Done, create the world, the terrain matches the preview around 0,0.
  - [ ] Spawn (0.5.0): Set Spawn, click a spot on land far from 0,0 (a pin appears; the line under the map shows it),
        Done, create the world: you spawn there (within the usual ~10-block spawn spread). Repeat with a spot in the
        ocean: you spawn on the nearest land. Leave and rejoin: spawn unchanged. Clear Spawn gives the normal spawn.
        Picking a preset keeps the chosen spawn; saved presets do not contain it.
  - [ ] Random (0.6.0): the pin lands on dry land near a river/lake/stream, not on a mountain; pressing again moves it.
  - [ ] Zoomed in (2k or closer), mountain streams appear as light-blue lines after a moment; the read-out says
        "mountain stream" over them.
- [ ] Streams (0.6.0, new world with the TerrainForged preset): fly over hills and mountains.
  - [ ] Streams run downhill in narrow channels (1-3 wide) and end in a river, lake, the sea, or a small pool.
  - [ ] Waterfalls pour as you approach (pools with 3-7 block drops on steep slopes); no water hanging in the air,
        no water spreading over the land beside a stream, no walls of sand/dirt along them.
  - [ ] Chunk loading still feels fine in mountains (first chunks of a new area trace the nearby streams).
  - [ ] A world created with 0.5 and opened in 0.6: new chunks have no streams (unchanged terrain).
  - [ ] 0.7: stream water is level with its banks (no water at the bottom of dirt trenches); streams follow valley
        floors and join; small mountain lakes where streams meet a hollow, with the stream leaving at the low end.
- [ ] New default: Create World with no changes gives the TerrainForged preset (taller mountains, more hills); the
      "ReTerraForged" preset gives the 0.5 default. An old auto-written `config/terrainforged.json` was renamed to
      `terrainforged.json.old` and regenerated (see the log).
- [ ] Snowy mountains: even snow cover, bare stone only on the steepest faces (0.4.0 change).
- [ ] Rivers and lakes hold water up to sea level; no floating water.
- [ ] Villages and other structures generate.
- [ ] `/locate biome minecraft:river` and `/locate structure minecraft:village_plains` work.

Tall preset (World Type: TerrainForged (Tall 2048)):

- [ ] World creates and loads; note the time to first spawn.
- [ ] Fly around for a few minutes: chunk loading speed, FPS, lighting on high mountains (no dark/black faces).
- [ ] Structures at height (villages on plateaus, pillager outposts), trial chambers underground.
- [ ] F3: peaks reach several hundred blocks; no terrain cut off at the top of the world.

Performance (both presets):

- [ ] Fly in a straight line with elytra or creative flight for 2 minutes; watch chunk loading.
      0.4.0 generates each 128×128-block tile's cells on a small thread pool (half your CPU cores, at least 2).
- [ ] On a server, check the tick time (`/tick query` or a profiler) while players explore new land.

## 3. Voxy (Fabric only)

Voxy is a client-side level-of-detail renderer: it builds its far-away terrain from chunks the game generates and
loads ("converts terrain to LODs as you explore"; existing worlds can be imported with
`/voxy import world "<world name>"`). It does not generate terrain itself, so it shows TerrainForged terrain like any
other world — no special code is needed. Things to check:

- [ ] Install Voxy (and its required dependencies, see its Modrinth page) with TerrainForged on Fabric, explore, and
      look at distant mountains: LODs should match the real terrain.
- [ ] Tall preset: check that Voxy renders terrain above y=320 (if it has its own height limit, mention it on the
      mod page).
- [ ] Optional multiplayer: the "LOD Server Support" add-on can generate unexplored chunks on the server for Voxy
      clients; it uses the server's generator, so it produces TerrainForged terrain. It is expensive; pre-generating
      with Chunky is recommended by that add-on.

## 4. Upload (CurseForge and Modrinth)

| Field | Value |
|---|---|
| Name | TerrainForged |
| Summary | Realistic world generation for Fabric and Forge 1.21.11: huge continents, mountain ranges, eroded valleys, winding rivers, lakes and climate-driven biomes. Based on ReTerraForged/TerraForged. |
| Icon | `docs/images/icon_512.png` |
| License | MIT |
| Categories | World Generation, Biomes (Modrinth: worldgen; CurseForge: World Gen) |
| Loaders | Fabric (requires Fabric API), Forge |
| Game version | 1.21.11 |
| Java | 21 |
| Environment | Server: required. Client: optional (needed for the Customize screen) |
| Dependencies | Fabric: Fabric API (required). Forge: none. |
| Version number | 0.7.0+fabric-1.21.11 / 0.7.0+forge-1.21.11 |
| Release channel | Beta (until the in-game checks above are done), then Release |

Use the long description from the project chat (or the README "What it does" section), and paste the changelog below
as the version's changelog.

## 5. Changelog 0.7.0 (beta)

- Improved mountain streams: channels are full to the banks, streams follow valley floors and join each other,
  every stream flows into a river, lake, the sea or a new small mountain lake, and dips along the way become pools.
- New: small mountain lakes where streams fill a hollow, with the stream flowing on from the lake's low end.
- Rivers are unchanged.

## Changelog 0.6.0 (beta)

- New: mountain streams and waterfalls — streams run down hills and mountains into rivers, lakes and the sea, with
  rapids, pools and 3-7 block waterfalls (bigger over cliffs). Water is always walled in by terrain: no floating water.
  Settings on the Rivers page; shown on the preview map.
- New default preset "TerrainForged": taller mountains, more hills and highlands, more land, rivers and lakes, plus
  streams. ReTerraForged's default is still available as the "ReTerraForged" preset.
- New: Random spawn button — picks dry land near a river, lake or stream, away from the mountains.
- Worlds created with older versions keep generating as before (no streams added to them).

## Changelog 0.5.0 (beta)

- New: choose the spawn point on the Customize screen's map (Set Spawn + click, or right-click the map). Spots in
  water move to the nearest dry land. Clear Spawn returns to the automatic spawn.
- Terrain generation is unchanged from 0.4.0 (same seeds give the same terrain).

## Changelog 0.4.0

- New: presets on the Customize screen — ReTerraForged's Default, Beautiful, Huge Biomes, Lite and Vanilla-ish, plus
  Archipelago and Alpine. Save your own presets by name, load them later, delete them.
- New: bigger world preview (256×256) with Relief, Climate and Contour views, mouse-wheel zoom, drag to pan, and a
  read-out of coordinates, height, water/terrain and climate under the cursor.
- New: continent type and biome-edge noise settings (needed by some ReTerraForged presets).
- Improved: snowy mountains keep an even snow cover; only the steepest faces show bare stone.
- Improved: faster chunk generation — terrain tiles are generated in parallel.
- Added the mod icon (shown in the Fabric and Forge mod lists).
- Terrain generation is otherwise unchanged from 0.3.0 (same seeds give the same terrain).
