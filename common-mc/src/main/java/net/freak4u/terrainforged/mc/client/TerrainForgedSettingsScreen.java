package net.freak4u.terrainforged.mc.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.blaze3d.platform.NativeImage;

import net.freak4u.terrainforged.engine.config.BuiltInPresets;
import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.config.TerrainSettings.Key;
import net.freak4u.terrainforged.engine.preview.ReliefRenderer;
import net.freak4u.terrainforged.engine.preview.SpawnPicker;
import net.freak4u.terrainforged.engine.terrain.RtfTerrain;
import net.freak4u.terrainforged.engine.worldgen.ColumnSample;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;
import net.freak4u.terrainforged.mc.EngineDensityFunction;
import net.freak4u.terrainforged.mc.GeneratorSettingsUtil;
import net.freak4u.terrainforged.mc.TerrainForgedConfig;
import net.freak4u.terrainforged.mc.TerrainForgedMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * World-creation "Customize" screen for TerrainForged.
 * <ul>
 *   <li>every engine setting as a slider, on seven pages (world, climate, terrain, terrain types, rivers & lakes,
 *       erosion, decoration);</li>
 *   <li>presets: ReTerraForged's built-in presets plus TerrainForged extras, and the player's own presets, which can
 *       be saved under a name and deleted (stored in {@code config/terrainforged/presets});</li>
 *   <li>a live 256x256 preview of the world for the typed seed (RTF's preview path), with relief, climate and contour
 *       views, mouse-wheel zoom, drag to pan, and a read-out of the position under the cursor;</li>
 *   <li>a spawn point picked on the map (right-click, or "Set Spawn" then click), placed by {@code SpawnPlacer} when
 *       the world first starts.</li>
 * </ul>
 * "Done" writes the settings into the new world's generator (and therefore its level.dat); "Save as Default" also
 * stores them in the config file used for future worlds. Made by Freak4U.
 */
public final class TerrainForgedSettingsScreen extends Screen {
    private static final Identifier PREVIEW_TEXTURE = TerrainForgedMod.id("preview");
    private static final ResourceKey<NormalNoise.NoiseParameters> SEED_PROBE = ResourceKey.create(Registries.NOISE, TerrainForgedMod.id("seed_probe"));
    private static final int TILES = 2; // 2x2 RTF preview tiles
    private static final int PREVIEW_PIXELS = RtfTerrain.TILE_BLOCKS * TILES;
    private static final int[] ZOOMS = {2, 4, 8, 16, 32, 64};
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TerrainForged preview");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private enum Category {
        WORLD("world"), CLIMATE("climate"), TERRAIN("terrain"), TERRAIN_TYPES("terrain_types"), RIVERS("rivers"),
        EROSION("erosion"), DECORATION("decoration");

        final String id;

        Category(String id) {
            this.id = id;
        }

        static Category of(Key k) {
            String[] parts = k.id.split("\\.");
            return switch (parts[0]) {
                case "world" -> WORLD;
                case "climate" -> CLIMATE;
                case "terrain" -> parts.length > 2 ? TERRAIN_TYPES : TERRAIN;
                case "rivers" -> RIVERS;
                case "erosion" -> EROSION;
                default -> DECORATION;
            };
        }

        List<Key> keys() {
            List<Key> out = new ArrayList<>();
            for (Key k : Key.values()) if (of(k) == this && !k.isWorldOnly()) out.add(k);
            return out;
        }
    }

    private final CreateWorldScreen parent;
    private final WorldCreationContext context;
    private final TerrainForgedConfig.Profile profile;
    private final int minY, maxY;
    private TerrainSettings settings;
    private Category category = Category.TERRAIN;
    private int page;
    private int zoom = 4;
    private int centerX, centerZ;
    private ReliefRenderer.Mode mode = ReliefRenderer.Mode.RELIEF;

    // presets: name -> settings (built-ins first), plus which entries are the player's own
    private final Map<String, TerrainSettings> presets = new LinkedHashMap<>();
    private final List<String> userPresets = new ArrayList<>();
    private int presetIndex;
    private EditBox presetName;
    private String typedPresetName = "";

    // preview state
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicReference<int[]> finishedPixels = new AtomicReference<>();
    private final AtomicReference<ReliefRenderer.Map> finishedMap = new AtomicReference<>();
    private ReliefRenderer.Map shownMap;
    private volatile boolean computing;
    private boolean dirty = true;
    private long lastChange;
    private boolean hasTexture;
    private boolean dragging;
    private double dragAccumX, dragAccumZ, dragMoved;
    private boolean pickingSpawn;
    private String status = "";
    private long statusUntil;
    private Long cachedWorldSeed;
    private long cachedEngineSeed;

    public TerrainForgedSettingsScreen(CreateWorldScreen parent, WorldCreationContext context, TerrainForgedConfig.Profile profile) {
        super(Component.translatable("terrainforged.screen.title"));
        this.parent = parent;
        this.context = context;
        this.profile = profile;
        EngineDensityFunction f = GeneratorSettingsUtil.findEngineFunction(context.selectedDimensions().overworld());
        this.settings = f != null ? f.settings() : TerrainForgedConfig.defaults(profile);
        this.minY = f != null ? f.minY() : -64;
        this.maxY = f != null ? f.maxY() : 320;
        reloadPresets();
    }

    // ------------------------------------------------------------------------------------------------------ presets

    private void reloadPresets() {
        presets.clear();
        userPresets.clear();
        presets.putAll(BuiltInPresets.all());
        for (String name : TerrainForgedConfig.listUserPresets()) {
            TerrainSettings s = TerrainForgedConfig.loadUserPreset(name);
            if (s == null) continue;
            String key = presets.containsKey(name) ? name + " (saved)" : name;
            presets.put(key, s);
            userPresets.add(key);
        }
        presetIndex = Math.min(presetIndex, presets.size() - 1);
    }

    private String presetAt(int index) {
        return new ArrayList<>(presets.keySet()).get(Math.floorMod(index, presets.size()));
    }

    /** Built-in presets are made for the standard height; on the Tall preset keep the tall terrain scale. */
    private TerrainSettings adaptToProfile(TerrainSettings s, boolean builtIn) {
        if (!builtIn || profile != TerrainForgedConfig.Profile.TALL) return s;
        TerrainSettings tall = TerrainForgedConfig.defaults(profile);
        return s.with(Key.TERRAIN_HEIGHT, tall.get(Key.TERRAIN_HEIGHT));
    }

    private void loadPreset(int index) {
        presetIndex = Math.floorMod(index, presets.size());
        String name = presetAt(presetIndex);
        settings = adaptToProfile(presets.get(name), !userPresets.contains(name)).withSpawnOf(settings);
        if (userPresets.contains(name)) typedPresetName = name.replace(" (saved)", "");
        showStatus(Component.translatable("terrainforged.screen.preset_loaded", name).getString());
        rebuildWidgets();
        markDirty(true);
    }

    private void savePreset() {
        String name = TerrainForgedConfig.sanitizePresetName(presetName != null ? presetName.getValue() : typedPresetName);
        if (name.isEmpty()) {
            showStatus(Component.translatable("terrainforged.screen.preset_name_needed").getString());
            return;
        }
        boolean ok = TerrainForgedConfig.saveUserPreset(name, settings);
        reloadPresets();
        List<String> keys = new ArrayList<>(presets.keySet());
        int idx = keys.indexOf(name);
        if (idx < 0) idx = keys.indexOf(name + " (saved)");
        if (idx >= 0) presetIndex = idx;
        typedPresetName = name;
        showStatus(Component.translatable(ok ? "terrainforged.screen.preset_saved" : "terrainforged.screen.save_failed", name).getString());
        rebuildWidgets();
    }

    private void deletePreset() {
        String name = presetAt(presetIndex);
        if (!userPresets.contains(name)) {
            showStatus(Component.translatable("terrainforged.screen.preset_builtin").getString());
            return;
        }
        boolean ok = TerrainForgedConfig.deleteUserPreset(name.replace(" (saved)", ""));
        reloadPresets();
        presetIndex = 0;
        showStatus(Component.translatable(ok ? "terrainforged.screen.preset_deleted" : "terrainforged.screen.save_failed", name).getString());
        rebuildWidgets();
    }

    private void showStatus(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    // ------------------------------------------------------------------------------------------------------ layout

    private int panelWidth() {
        return Math.max(160, Math.min(260, (int) (width * 0.42)));
    }

    private int top() {
        return 44;
    }

    private int bottomReserved() {
        return 82; // preset row + button row + status line
    }

    private int rowsPerPage() {
        return Math.max(3, (height - top() - bottomReserved() - 22) / 22);
    }

    private int previewX() {
        return 10 + panelWidth() + 14;
    }

    private int previewSize() {
        return Math.max(64, Math.min(width - previewX() - 10, height - top() - bottomReserved() - 12));
    }

    @Override
    protected void init() {
        int left = 10, pw = panelWidth();
        addRenderableWidget(Button.builder(categoryLabel(), b -> {
            category = Category.values()[(category.ordinal() + 1) % Category.values().length];
            page = 0;
            rebuildWidgets();
        }).bounds(left, 20, pw, 20).build());

        List<Key> keys = category.keys();
        int rows = rowsPerPage();
        int pages = (keys.size() + rows - 1) / rows;
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * rows, r = 0; i < keys.size() && r < rows; i++, r++) {
            addRenderableWidget(new SettingSlider(left, top() + r * 22, pw, keys.get(i)));
        }
        if (pages > 1) {
            int y = top() + rows * 22;
            Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(left, y, 20, 20).build());
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(left + pw - 20, y, 20, 20).build());
            prev.active = page > 0;
            next.active = page < pages - 1;
        }

        // preview controls (above the map)
        int px = previewX(), size = previewSize();
        int[] tw = buttonWidths(size, "terrainforged.screen.mode.height", "terrainforged.screen.zoom", "terrainforged.screen.recenter",
            "terrainforged.screen.spawn_clear", "terrainforged.screen.spawn_random");
        int[] bx = new int[5];
        for (int k = 1; k < 5; k++) bx[k] = bx[k - 1] + tw[k - 1] + 4;
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            mode = ReliefRenderer.Mode.values()[(mode.ordinal() + 1) % ReliefRenderer.Mode.values().length];
            b.setMessage(modeLabel());
            recolour();
        }).bounds(px, 20, tw[0], 20).build()).setTooltip(Tooltip.create(Component.translatable("terrainforged.screen.mode.desc")));
        addRenderableWidget(Button.builder(zoomLabel(), b -> {
            zoom = (zoom + 1) % ZOOMS.length;
            b.setMessage(zoomLabel());
            markDirty(true);
        }).bounds(px + bx[1], 20, tw[1], 20).build()).setTooltip(Tooltip.create(Component.translatable("terrainforged.screen.zoom.desc")));
        addRenderableWidget(Button.builder(Component.translatable("terrainforged.screen.recenter"), b -> {
            centerX = 0;
            centerZ = 0;
            markDirty(true);
        }).bounds(px + bx[2], 20, tw[2], 20).build()).setTooltip(Tooltip.create(Component.translatable("terrainforged.screen.recenter.desc")));
        addRenderableWidget(Button.builder(spawnLabel(), b -> {
            if (settings.hasSpawn()) {
                settings = settings.withoutSpawn();
                pickingSpawn = false;
                showStatus(Component.translatable("terrainforged.screen.spawn_cleared").getString());
            } else {
                pickingSpawn = !pickingSpawn;
                if (pickingSpawn) showStatus(Component.translatable("terrainforged.screen.spawn_pick_hint").getString());
            }
            b.setMessage(spawnLabel());
        }).bounds(px + bx[3], 20, tw[3], 20).build())
            .setTooltip(Tooltip.create(Component.translatable("terrainforged.screen.spawn.desc")));
        addRenderableWidget(Button.builder(Component.translatable("terrainforged.screen.spawn_random"), b -> randomSpawn())
            .bounds(px + bx[4], 20, tw[4], 20).build()).setTooltip(Tooltip.create(Component.translatable("terrainforged.screen.spawn_random.desc")));

        // preset row
        int y1 = height - 54, full = width - 20;
        int arrowW = 20, nameW = Math.max(80, full / 4), saveW = Math.max(70, full / 7), delW = Math.max(60, full / 8);
        int labelW = Math.max(80, full - 2 * arrowW - nameW - saveW - delW - 5 * 4);
        int x = 10;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> loadPreset(presetIndex - 1)).bounds(x, y1, arrowW, 20).build());
        x += arrowW + 4;
        Button presetButton = addRenderableWidget(Button.builder(presetLabel(), b -> loadPreset(presetIndex)).bounds(x, y1, labelW, 20).build());
        presetButton.setTooltip(Tooltip.create(Component.translatable("terrainforged.screen.preset.desc")));
        x += labelW + 4;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> loadPreset(presetIndex + 1)).bounds(x, y1, arrowW, 20).build());
        x += arrowW + 4;
        presetName = new EditBox(font, x, y1, nameW, 20, Component.translatable("terrainforged.screen.preset_name"));
        presetName.setMaxLength(40);
        presetName.setHint(Component.translatable("terrainforged.screen.preset_name"));
        presetName.setValue(typedPresetName);
        presetName.setResponder(v -> typedPresetName = v);
        addRenderableWidget(presetName);
        x += nameW + 4;
        addRenderableWidget(Button.builder(Component.translatable("terrainforged.screen.preset_save"), b -> savePreset()).bounds(x, y1, saveW, 20).build());
        x += saveW + 4;
        Button delete = addRenderableWidget(Button.builder(Component.translatable("terrainforged.screen.preset_delete"), b -> deletePreset()).bounds(x, y1, delW, 20).build());
        delete.active = userPresets.contains(presetAt(presetIndex));

        // bottom row
        int bw = Math.min(110, (width - 40) / 4), y2 = height - 28;
        x = width / 2 - 2 * bw - 6;
        addRenderableWidget(Button.builder(Component.translatable("terrainforged.screen.reset"), b -> {
            settings = TerrainForgedConfig.defaults(profile).withSpawnOf(settings); // the spawn has its own Clear button
            rebuildWidgets();
            markDirty(true);
        }).bounds(x, y2, bw, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.translatable("terrainforged.screen.save_default"), b -> {
            boolean ok = TerrainForgedConfig.save(profile, settings);
            showStatus(Language.getInstance().getOrDefault(ok ? "terrainforged.screen.saved" : "terrainforged.screen.save_failed"));
        }).bounds(x + bw + 4, y2, bw, 20).build());
        save.setTooltip(Tooltip.create(Component.translatable("terrainforged.screen.save_default.desc")));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> applyAndClose()).bounds(x + 2 * (bw + 4), y2, bw, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 3 * (bw + 4), y2, bw, 20).build());
    }

    private Component categoryLabel() {
        return Component.translatable("terrainforged.screen.category", Component.translatable("terrainforged.category." + category.id));
    }

    private Component presetLabel() {
        String name = presetAt(presetIndex);
        return Component.translatable(userPresets.contains(name) ? "terrainforged.screen.preset_user" : "terrainforged.screen.preset", name);
    }

    /** Splits {@code total} pixels (minus gaps) between buttons in proportion to their (longest) labels. */
    private int[] buttonWidths(int total, String... keys) {
        int[] want = new int[keys.length];
        int sum = 0;
        for (int k = 0; k < keys.length; k++) {
            want[k] = font.width(Component.translatable(keys[k], "8.2k")) + 10;
            sum += want[k];
        }
        int avail = total - 4 * (keys.length - 1), used = 0;
        int[] out = new int[keys.length];
        for (int k = 0; k < keys.length; k++) {
            out[k] = k == keys.length - 1 ? Math.max(20, avail - used) : Math.max(20, want[k] * avail / sum);
            used += out[k];
        }
        return out;
    }

    /** "Random" spawn button: see {@link SpawnPicker}. */
    private void randomSpawn() {
        ReliefRenderer.Map map = shownMap;
        if (map == null) return;
        int[] spot = SpawnPicker.pick(map, random);
        if (spot == null) {
            showStatus(Component.translatable("terrainforged.screen.spawn_random_none").getString());
            return;
        }
        int wx = spot[0], wz = spot[1];
        settings = settings.withSpawn(wx, wz);
        pickingSpawn = false;
        showStatus(Component.translatable("terrainforged.screen.spawn_random_set", wx, wz).getString());
        rebuildWidgets();
    }

    private final java.util.Random random = new java.util.Random();

    private Component spawnLabel() {
        return Component.translatable(settings.hasSpawn() ? "terrainforged.screen.spawn_clear"
            : pickingSpawn ? "terrainforged.screen.spawn_picking" : "terrainforged.screen.spawn_set");
    }

    private Component modeLabel() {
        return Component.translatable("terrainforged.screen.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private Component zoomLabel() {
        int span = ZOOMS[zoom] * PREVIEW_PIXELS;
        return Component.translatable("terrainforged.screen.zoom", span >= 1000 ? String.format(Locale.ROOT, "%.1fk", span / 1000.0) : Integer.toString(span));
    }

    // ------------------------------------------------------------------------------------------------------ apply

    private void applyAndClose() {
        TerrainSettings chosen = settings;
        parent.getUiState().updateDimensions((access, dims) -> {
            ChunkGenerator g = GeneratorSettingsUtil.withSettings(dims.overworld(), chosen);
            return g == null ? dims : dims.replaceOverworldGenerator(access, g);
        });
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        generation.incrementAndGet(); // cancels a running preview
        if (hasTexture) {
            minecraft.getTextureManager().release(PREVIEW_TEXTURE);
            hasTexture = false;
        }
        super.removed();
    }

    // ------------------------------------------------------------------------------------------------------ mouse

    private boolean overPreview(double mx, double my) {
        int px = previewX(), size = previewSize();
        return mx >= px && my >= top() && mx < px + size && my < top() + size;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (overPreview(mx, my) && scrollY != 0) {
            int next = Math.max(0, Math.min(ZOOMS.length - 1, zoom + (scrollY > 0 ? -1 : 1)));
            if (next != zoom) {
                zoom = next;
                rebuildWidgets();
                markDirty(false);
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && overPreview(event.x(), event.y())) { // right-click: put the spawn here
            setSpawnAt(event.x(), event.y());
            return true;
        }
        if (event.button() == 0 && overPreview(event.x(), event.y())) {
            dragging = true;
            dragAccumX = 0;
            dragAccumZ = 0;
            dragMoved = 0;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            double blocksPerScreenPixel = ZOOMS[zoom] * PREVIEW_PIXELS / (double) previewSize();
            dragAccumX -= dx * blocksPerScreenPixel;
            dragAccumZ -= dy * blocksPerScreenPixel;
            dragMoved += Math.abs(dx) + Math.abs(dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            if (pickingSpawn && dragMoved < 4) { // "Set Spawn" then a plain click on the map
                dragAccumX = 0;
                dragAccumZ = 0;
                setSpawnAt(event.x(), event.y());
                return true;
            }
            if (Math.abs(dragAccumX) >= 1 || Math.abs(dragAccumZ) >= 1) {
                centerX += (int) Math.round(dragAccumX);
                centerZ += (int) Math.round(dragAccumZ);
                markDirty(true);
            }
            dragAccumX = 0;
            dragAccumZ = 0;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Sets the spawn point to the map position under the screen point (mx, my). */
    private void setSpawnAt(double mx, double my) {
        ReliefRenderer.Map map = shownMap;
        if (map == null) return;
        int size = previewSize();
        int pxl = Math.max(0, Math.min(map.width - 1, (int) ((mx - previewX()) * (double) map.width / size)));
        int pzl = Math.max(0, Math.min(map.height - 1, (int) ((my - top()) * (double) map.height / size)));
        int wx = Math.round(map.originX + (pxl + 0.5f) * map.blocksPerPixel), wz = Math.round(map.originZ + (pzl + 0.5f) * map.blocksPerPixel);
        settings = settings.withSpawn(wx, wz);
        pickingSpawn = false;
        ColumnSample c = map.at(pxl, pzl);
        boolean wet = c.height < map.seaLevel + 1 || c.is(RtfTerrain.FLAG_OCEAN) || c.is(RtfTerrain.FLAG_RIVER)
            || c.is(RtfTerrain.FLAG_LAKE) || c.is(RtfTerrain.FLAG_WETLAND);
        showStatus(Component.translatable(wet ? "terrainforged.screen.spawn_set_water" : "terrainforged.screen.spawn_set_to", wx, wz).getString());
        rebuildWidgets(); // refresh the spawn button label
    }

    // ------------------------------------------------------------------------------------------------------ preview

    private void markDirty(boolean immediate) {
        dirty = true;
        lastChange = immediate ? 0 : System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        int[] pixels = finishedPixels.getAndSet(null);
        if (pixels != null) {
            shownMap = finishedMap.getAndSet(null);
            upload(pixels);
        }
        if (dirty && System.currentTimeMillis() - lastChange > 350) {
            dirty = false;
            startPreview();
        }
        if (!status.isEmpty() && System.currentTimeMillis() > statusUntil) status = "";
    }

    private void recolour() {
        ReliefRenderer.Map map = shownMap;
        if (map != null) upload(ReliefRenderer.colour(map, mode));
    }

    private void startPreview() {
        int gen = generation.incrementAndGet();
        TerrainSettings s = settings.fitToWorld(minY, maxY);
        int bpp = ZOOMS[zoom], cx = centerX, cz = centerZ;
        ReliefRenderer.Mode m = mode;
        long worldSeed = worldSeed();
        computing = true;
        WORKER.execute(() -> {
            if (generation.get() != gen) return;
            try {
                TerrainEngine engine = previewEngine(engineSeed(worldSeed), s.withoutSpawn());
                if (generation.get() != gen) return;
                ReliefRenderer.Map map = ReliefRenderer.preview(engine, cx, cz, bpp, TILES);
                if (generation.get() != gen) return;
                finishedMap.set(map);
                finishedPixels.set(ReliefRenderer.colour(map, m));
                // second stage: mountain streams (tracing them takes a moment; the terrain is already shown)
                if (bpp <= ReliefRenderer.STREAM_MAX_BPP && engine.streams().enabled()) {
                    ReliefRenderer.overlayStreams(map, engine);
                    if (generation.get() == gen) {
                        finishedMap.set(map);
                        finishedPixels.set(ReliefRenderer.colour(map, mode));
                    }
                }
            } catch (RuntimeException ex) {
                TerrainForgedMod.LOGGER.warn("TerrainForged preview failed", ex);
            } finally {
                if (generation.get() == gen) computing = false;
            }
        });
    }

    // only touched on the preview worker thread: re-used while seed and terrain settings stay the same, so panning
    // and zooming keep RTF's and the stream network's caches
    private TerrainEngine lastEngine;
    private long lastEngineSeed;
    private TerrainSettings lastEngineSettings;

    private TerrainEngine previewEngine(long seed, TerrainSettings s) {
        if (lastEngine == null || lastEngineSeed != seed || !s.equals(lastEngineSettings)) {
            lastEngine = new TerrainEngine(seed, s, minY);
            lastEngineSeed = seed;
            lastEngineSettings = s;
        }
        return lastEngine;
    }

    private long worldSeed() {
        OptionalLong parsed = WorldOptions.parseSeed(parent.getUiState().getSeed());
        return parsed.isPresent() ? parsed.getAsLong() : context.options().seed();
    }

    /** The engine seed a world with this seed will use (same derivation as {@link EngineDensityFunction}). */
    private long engineSeed(long worldSeed) {
        if (cachedWorldSeed != null && cachedWorldSeed == worldSeed) return cachedEngineSeed;
        long result;
        try {
            RandomState state = RandomState.create(NoiseGeneratorSettings.dummy(), context.worldgenLoadContext().lookupOrThrow(Registries.NOISE), worldSeed);
            result = EngineDensityFunction.seedFrom(state.getOrCreateNoise(SEED_PROBE));
        } catch (RuntimeException ex) {
            result = worldSeed;
        }
        cachedWorldSeed = worldSeed;
        cachedEngineSeed = result;
        return result;
    }

    private void upload(int[] argb) {
        NativeImage image = new NativeImage(PREVIEW_PIXELS, PREVIEW_PIXELS, false);
        for (int z = 0; z < PREVIEW_PIXELS; z++) {
            for (int x = 0; x < PREVIEW_PIXELS; x++) image.setPixel(x, z, argb[z * PREVIEW_PIXELS + x]);
        }
        if (hasTexture) minecraft.getTextureManager().release(PREVIEW_TEXTURE);
        minecraft.getTextureManager().register(PREVIEW_TEXTURE, new DynamicTexture(() -> "terrainforged_preview", image));
        hasTexture = true;
    }

    // ------------------------------------------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFFFF);
        int px = previewX(), py = top(), size = previewSize();
        g.fill(px - 1, py - 1, px + size + 1, py + size + 1, 0xFF000000);
        if (hasTexture) {
            int ox = 0, oz = 0;
            if (dragging) { // move the current image with the mouse until the new one arrives
                double scale = size / (double) (ZOOMS[zoom] * PREVIEW_PIXELS);
                ox = (int) Math.round(-dragAccumX * scale);
                oz = (int) Math.round(-dragAccumZ * scale);
                g.enableScissor(px, py, px + size, py + size);
            }
            g.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_TEXTURE, px + ox, py + oz, 0, 0, size, size, PREVIEW_PIXELS, PREVIEW_PIXELS, PREVIEW_PIXELS, PREVIEW_PIXELS);
            if (dragging) g.disableScissor();
            int c = px + size / 2, d = py + size / 2;
            g.fill(c - 4, d, c + 5, d + 1, 0xB0FFFFFF);
            g.fill(c, d - 4, c + 1, d + 5, 0xB0FFFFFF);
            drawSpawnMarker(g, px, py, size, ox, oz);
        }
        if (computing || dirty) g.drawString(font, Component.translatable("terrainforged.screen.generating"), px + 4, py + 4, 0xFFFFFFFF);
        // read-out under the map: hovered position, or the map centre
        String info = readout(mouseX, mouseY);
        g.drawString(font, info, px, py + size + 4, 0xFFC8C8C8);
        Component spawn = settings.hasSpawn()
            ? Component.translatable("terrainforged.screen.spawn_at", settings.spawnX(), settings.spawnZ())
            : Component.translatable(pickingSpawn ? "terrainforged.screen.spawn_pick_hint" : "terrainforged.screen.spawn_auto");
        g.drawString(font, spawn, px, py + size + 15, settings.hasSpawn() ? 0xFFFFD866 : 0xFFA0A0A0);
        if (!status.isEmpty()) g.drawCenteredString(font, status, width / 2, height - 66, 0xFF80FF80);
    }

    /** A yellow pin (with a dark outline) where the chosen spawn is, if it is inside the shown map. */
    private void drawSpawnMarker(GuiGraphics g, int px, int py, int size, int ox, int oz) {
        ReliefRenderer.Map map = shownMap;
        if (map == null || !settings.hasSpawn()) return;
        double scale = size / (double) map.width;
        int sx = px + ox + (int) Math.floor((settings.spawnX() - map.originX) / map.blocksPerPixel * scale);
        int sz = py + oz + (int) Math.floor((settings.spawnZ() - map.originZ) / map.blocksPerPixel * scale);
        if (sx < px + 3 || sz < py + 3 || sx > px + size - 4 || sz > py + size - 4) return;
        g.fill(sx - 3, sz - 3, sx + 4, sz + 4, 0xFF000000);
        g.fill(sx - 2, sz - 2, sx + 3, sz + 3, 0xFFFFD020);
        g.fill(sx, sz - 9, sx + 1, sz - 3, 0xFF000000); // flag pole
        g.fill(sx + 1, sz - 9, sx + 5, sz - 6, 0xFFFF4040); // flag
    }

    private String readout(int mouseX, int mouseY) {
        ReliefRenderer.Map map = shownMap;
        if (map == null || !overPreview(mouseX, mouseY) || dragging) {
            return Component.translatable("terrainforged.screen.center", centerX, centerZ).getString();
        }
        int size = previewSize();
        int pxl = (int) ((mouseX - previewX()) * (double) map.width / size);
        int pzl = (int) ((mouseY - top()) * (double) map.height / size);
        ColumnSample c = map.at(pxl, pzl);
        int wx = Math.round(map.originX + pxl * map.blocksPerPixel), wz = Math.round(map.originZ + pzl * map.blocksPerPixel);
        String feature = c.is(RtfTerrain.FLAG_STREAM) && c.height >= map.seaLevel ? "stream" : c.is(RtfTerrain.FLAG_RIVER) ? "river" : c.is(RtfTerrain.FLAG_LAKE) ? "lake" : c.is(RtfTerrain.FLAG_WETLAND) ? "wetland"
            : c.is(RtfTerrain.FLAG_OCEAN) ? "ocean" : c.is(RtfTerrain.FLAG_VOLCANO) ? "volcano" : c.is(RtfTerrain.FLAG_BEACH) ? "coast" : "land";
        String temp = c.vTemperature < -0.45 ? "frozen" : c.vTemperature < -0.15 ? "cold" : c.vTemperature < 0.2 ? "temperate" : c.vTemperature < 0.55 ? "warm" : "hot";
        String wet = c.vHumidity < -0.35 ? "arid" : c.vHumidity < -0.1 ? "dry" : c.vHumidity < 0.1 ? "average" : c.vHumidity < 0.3 ? "humid" : "wet";
        return Component.translatable("terrainforged.screen.readout", wx, wz, (int) Math.floor(c.height),
            Component.translatable("terrainforged.feature." + feature), Component.translatable("terrainforged.climate." + temp),
            Component.translatable("terrainforged.climate." + wet)).getString();
    }

    // ------------------------------------------------------------------------------------------------------ sliders

    private static boolean isLog(Key k) {
        return k.choices() == null && k.min > 0 && k.max / k.min >= 20;
    }

    private static double toSlider(Key k, double v) {
        if (isLog(k)) return Math.log(v / k.min) / Math.log(k.max / k.min);
        return (v - k.min) / (k.max - k.min);
    }

    private static double fromSlider(Key k, double t) {
        double v = isLog(k) ? k.min * Math.pow(k.max / k.min, t) : k.min + t * (k.max - k.min);
        if (k.isInteger()) {
            double step = k.choices() != null || k.isToggle() ? 1 : v >= 5000 ? 100 : v >= 1000 ? 50 : v >= 300 ? 10 : 1;
            v = Math.round(v / step) * step;
        } else {
            v = Math.round(v * 1000) / 1000.0;
        }
        return Math.max(k.min, Math.min(k.max, v));
    }

    private static String pretty(Key k) {
        String[] parts = k.id.split("\\.");
        String s = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private final class SettingSlider extends AbstractSliderButton {
        private final Key key;

        SettingSlider(int x, int y, int w, Key key) {
            super(x, y, w, 20, Component.empty(), toSlider(key, settings.get(key)));
            this.key = key;
            updateMessage();
            String desc = "terrainforged.setting." + key.id + ".desc";
            if (Language.getInstance().has(desc)) setTooltip(Tooltip.create(Component.translatable(desc)));
        }

        @Override
        protected void updateMessage() {
            double v = fromSlider(key, value);
            String[] choices = key.choices();
            String text = choices != null ? choices[(int) Math.max(0, Math.min(choices.length - 1, Math.round(v)))]
                : key.isToggle() ? (v >= 0.5 ? "ON" : "OFF")
                : key.isInteger() ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.2f", v);
            setMessage(Component.translatableWithFallback("terrainforged.setting." + key.id, pretty(key)).append(": " + text));
        }

        @Override
        protected void applyValue() {
            double v = fromSlider(key, value);
            if (v != settings.get(key)) {
                settings = settings.with(key, v);
                markDirty(false);
            }
        }
    }
}
