package net.freak4u.terrainforged.mc;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.freak4u.terrainforged.engine.config.TerrainSettings;

/**
 * Validated configuration-file fallback for world settings.
 *
 * <p>{@code config/terrainforged.json} (standard preset) and {@code config/terrainforged-tall.json} (tall preset)
 * hold the settings applied to <b>newly created</b> worlds. When a world is created the values are copied into
 * the world's own generator settings (level.dat), so editing this file never changes existing worlds.
 * Invalid values are clamped to their documented ranges; malformed JSON falls back to defaults and is reported
 * in the log, and the file is never overwritten once it exists.
 */
public final class TerrainForgedConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Profile {
        STANDARD("terrainforged.json", TerrainSettings.defaults()),
        TALL("terrainforged-tall.json", tallDefaults());

        final String file;
        final TerrainSettings defaults;

        Profile(String file, TerrainSettings defaults) {
            this.file = file;
            this.defaults = defaults;
        }
    }

    private TerrainForgedConfig() {}

    /** Defaults for the extended-height preset (build height -64..1984). */
    public static TerrainSettings tallDefaults() {
        // RTF terrain scales with the terrain height; a wider horizontal scale keeps slopes walkable
        return TerrainSettings.defaults()
            .with(TerrainSettings.Key.TERRAIN_HEIGHT, 900)
            .with(TerrainSettings.Key.VERTICAL_SCALE, 0.9)
            .with(TerrainSettings.Key.TERRAIN_SCALE, 1.6)
            .with(TerrainSettings.Key.CONTINENT_SCALE, 4500)
            .with(TerrainSettings.Key.REGION_SIZE, 1800);
    }

    /**
     * Version of the built-in defaults. 2 = TerrainForged's own default (0.6.0: taller mountains, more hills, rivers,
     * lakes, mountain streams); 1 (no marker) = ReTerraForged's Default, written by 0.2-0.5.
     */
    static final int DEFAULTS_VERSION = 2;
    private static final String VERSION_KEY = "_defaults_version";
    private static final String AUTO_README_END = "Delete this file to regenerate defaults.";

    /**
     * A config file that 0.2-0.5 wrote automatically (never edited from the Customize screen) still holds the old
     * defaults; it is renamed to {@code *.json.old} and regenerated so new worlds get the new defaults. Files saved
     * from the Customize screen ("Save as Default") are the player's choice and are never touched.
     */
    private static void migrate(Profile p, Path file) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return;
            JsonObject o = el.getAsJsonObject();
            if (o.has(VERSION_KEY)) return;
            JsonElement readme = o.get("_readme");
            if (readme == null || !readme.isJsonPrimitive() || !readme.getAsString().endsWith(AUTO_README_END)) return;
        } catch (Exception ex) {
            return; // unreadable files are reported by load()
        }
        try {
            Path old = file.resolveSibling(p.file + ".old");
            Files.move(file, old, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            TerrainForgedMod.LOGGER.info("{}: updated to the new TerrainForged defaults (previous file kept as {})", p.file, old.getFileName());
        } catch (IOException ex) {
            TerrainForgedMod.LOGGER.warn("Could not update {} to the new defaults: {}", file, ex.toString());
        }
    }

    public static void ensureDefaults() {
        for (Profile p : Profile.values()) {
            Path file = TerrainForgedMod.configDir().resolve(p.file);
            if (Files.exists(file)) migrate(p, file);
            if (Files.exists(file)) continue;
            try {
                Files.createDirectories(file.getParent());
                JsonObject root = new JsonObject();
                root.addProperty("_readme", "TerrainForged settings for NEW worlds using the '" + p.name().toLowerCase()
                    + "' preset. Values are copied into each world when it is created; existing worlds are unaffected. "
                    + "Out-of-range values are clamped. " + AUTO_README_END);
                root.addProperty(VERSION_KEY, DEFAULTS_VERSION);
                for (Map.Entry<String, Double> e : p.defaults.toPresetMap().entrySet()) root.addProperty(e.getKey(), e.getValue());
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, w);
                }
            } catch (IOException ex) {
                TerrainForgedMod.LOGGER.warn("Could not write default config {}: {}", file, ex.toString());
            }
        }
    }

    /** Reads a profile; never throws. */
    public static TerrainSettings load(Profile p) {
        Path file = TerrainForgedMod.configDir().resolve(p.file);
        Map<String, Double> values = new LinkedHashMap<>(p.defaults.toMap());
        if (!Files.exists(file)) return p.defaults;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) throw new IllegalStateException("root is not a JSON object");
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                if (!values.containsKey(e.getKey())) {
                    TerrainForgedMod.LOGGER.warn("{}: unknown setting '{}' ignored", p.file, e.getKey());
                    continue;
                }
                try {
                    values.put(e.getKey(), e.getValue().getAsDouble());
                } catch (RuntimeException bad) {
                    TerrainForgedMod.LOGGER.warn("{}: '{}' is not a number, using default", p.file, e.getKey());
                }
            }
        } catch (Exception ex) {
            TerrainForgedMod.LOGGER.error("{} is invalid ({}); using defaults for this world", file, ex.toString());
            return p.defaults;
        }
        TerrainSettings s = TerrainSettings.fromMap(values);
        for (Map.Entry<String, Double> e : values.entrySet()) {
            double clamped = s.toMap().get(e.getKey());
            if (clamped != e.getValue()) TerrainForgedMod.LOGGER.warn("{}: '{}'={} out of range, clamped to {}", p.file, e.getKey(), e.getValue(), clamped);
        }
        return s;
    }

    /** Built-in defaults of a profile. */
    public static TerrainSettings defaults(Profile p) {
        return p.defaults;
    }

    /**
     * Writes settings as the profile's defaults for future worlds (only on the user's explicit request from the
     * world-creation screen). Returns false if the file could not be written.
     */
    public static boolean save(Profile p, TerrainSettings settings) {
        Path file = TerrainForgedMod.configDir().resolve(p.file);
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_readme", "TerrainForged settings for NEW worlds using the '" + p.name().toLowerCase()
                + "' preset (saved from the Customize screen). Existing worlds are unaffected. Out-of-range values are clamped.");
            root.addProperty(VERSION_KEY, DEFAULTS_VERSION);
            for (Map.Entry<String, Double> e : settings.toPresetMap().entrySet()) root.addProperty(e.getKey(), e.getValue());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
            return true;
        } catch (IOException ex) {
            TerrainForgedMod.LOGGER.warn("Could not save config {}: {}", file, ex.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------- user presets

    /** Folder holding the presets saved from the Customize screen ({@code config/terrainforged/presets}). */
    public static Path presetDir() {
        return TerrainForgedMod.configDir().resolve("terrainforged").resolve("presets");
    }

    /** File-system-safe version of a preset name (letters, digits, space, dash, underscore; max 40). */
    public static String sanitizePresetName(String name) {
        String clean = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return clean.length() > 40 ? clean.substring(0, 40).trim() : clean;
    }

    /** Saved preset names, sorted. Never throws. */
    public static java.util.List<String> listUserPresets() {
        java.util.List<String> out = new java.util.ArrayList<>();
        Path dir = presetDir();
        if (!Files.isDirectory(dir)) return out;
        try (var stream = Files.list(dir)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".json"))
                .forEach(f -> out.add(f.getFileName().toString().replaceFirst("\\.json$", "")));
        } catch (IOException ex) {
            TerrainForgedMod.LOGGER.warn("Could not list presets in {}: {}", dir, ex.toString());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Writes a user preset; returns false (and logs) on failure or an empty name. */
    public static boolean saveUserPreset(String name, TerrainSettings settings) {
        String clean = sanitizePresetName(name);
        if (clean.isEmpty()) return false;
        Path file = presetDir().resolve(clean + ".json");
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_readme", "TerrainForged preset '" + clean + "' (saved from the Customize screen).");
            for (Map.Entry<String, Double> e : settings.toPresetMap().entrySet()) root.addProperty(e.getKey(), e.getValue());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
            return true;
        } catch (IOException ex) {
            TerrainForgedMod.LOGGER.warn("Could not save preset {}: {}", file, ex.toString());
            return false;
        }
    }

    /** Reads a user preset (unknown keys ignored, missing keys default, values clamped); null if unreadable. */
    public static TerrainSettings loadUserPreset(String name) {
        Path file = presetDir().resolve(sanitizePresetName(name) + ".json");
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return null;
            Map<String, Double> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                try {
                    values.put(e.getKey(), e.getValue().getAsDouble());
                } catch (RuntimeException ignored) {
                    // non-numeric entry: keep the default for that key
                }
            }
            return TerrainSettings.fromMap(values);
        } catch (Exception ex) {
            TerrainForgedMod.LOGGER.warn("Could not read preset {}: {}", file, ex.toString());
            return null;
        }
    }

    public static boolean deleteUserPreset(String name) {
        Path file = presetDir().resolve(sanitizePresetName(name) + ".json");
        try {
            return Files.deleteIfExists(file);
        } catch (IOException ex) {
            TerrainForgedMod.LOGGER.warn("Could not delete preset {}: {}", file, ex.toString());
            return false;
        }
    }
}
