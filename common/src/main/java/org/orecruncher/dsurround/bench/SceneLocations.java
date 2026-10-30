package org.orecruncher.dsurround.bench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Where each scene is in one world, kept in {@code dsurround-bench.json} in the world's folder: found by biome, or
 * marked by hand with /dsbench mark. The world's seed and the mods (with their versions) are kept with them: either
 * changing can move biomes, so the runner checks a remembered scene's biome before using it, and says which mods
 * differ.
 */
public final class SceneLocations {

    static final String FILE = "dsurround-bench.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * A remembered scene.
     *
     * @param biome      the biome there when it was found or marked
     * @param dimension  the dimension it is in
     * @param biomeShare how much of the ground around it was that biome, when found by biome (1 when marked)
     * @param marked     whether it was marked by hand, rather than found
     */
    public record Location(String biome, String dimension, double x, double y, double z, float yaw, float pitch,
                           double biomeShare, boolean marked) {
    }

    private final Path file;
    private final Map<String, Location> scenes = new LinkedHashMap<>();
    private long seed;
    // The mods the scenes were found with, id@version; empty if not known (none yet, or an older file)
    private List<String> mods = List.of();

    private SceneLocations(Path file) {
        this.file = file;
    }

    /**
     * The scenes remembered in a world's folder; none if there is no file yet
     */
    public static SceneLocations load(Path worldFolder) throws IOException {
        var locations = new SceneLocations(worldFolder.resolve(FILE));
        if (Files.exists(locations.file)) {
            var json = JsonParser.parseString(Files.readString(locations.file, StandardCharsets.UTF_8)).getAsJsonObject();
            locations.seed = json.has("seed") ? json.get("seed").getAsLong() : 0L;
            if (json.has("mods") && json.get("mods").isJsonArray()) {
                var mods = new ArrayList<String>();
                json.getAsJsonArray("mods").forEach(m -> mods.add(m.getAsString()));
                locations.mods = mods;
            }
            if (json.has("scenes"))
                for (var entry : json.getAsJsonObject("scenes").entrySet())
                    locations.scenes.put(entry.getKey(), GSON.fromJson(entry.getValue(), Location.class));
        }
        return locations;
    }

    public @Nullable Location get(String scene) {
        return this.scenes.get(scene);
    }

    public Map<String, Location> all() {
        return this.scenes;
    }

    /**
     * The mods loaded now that the scenes weren't found with, and those they were found with that aren't loaded now,
     * marked + and -; empty if the same (or none were remembered)
     */
    public List<String> modChanges() {
        if (this.mods.isEmpty())
            return List.of();
        var now = mods();
        var changes = new ArrayList<String>();
        now.stream().filter(m -> !this.mods.contains(m)).forEach(m -> changes.add("+" + m));
        this.mods.stream().filter(m -> !now.contains(m)).forEach(m -> changes.add("-" + m));
        return changes;
    }

    /**
     * Notes the mods loaded now as those the scenes were found with, if that isn't known (a file from before the list
     * was kept), so later sessions can say what changed
     */
    public void noteModsIfUnknown(long seed) throws IOException {
        if (!this.mods.isEmpty() || this.scenes.isEmpty())
            return;
        this.seed = seed;
        this.mods = mods();
        this.save();
    }

    public void put(String scene, Location location, long seed) throws IOException {
        this.scenes.put(scene, location);
        this.seed = seed;
        this.mods = mods();
        this.save();
    }

    private void save() throws IOException {
        var json = new JsonObject();
        json.addProperty("seed", this.seed);
        var mods = new JsonArray();
        this.mods.forEach(mods::add);
        json.add("mods", mods);
        var scenes = new JsonObject();
        this.scenes.forEach((name, location) -> scenes.add(name, GSON.toJsonTree(location)));
        json.add("scenes", scenes);
        Files.writeString(this.file, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    /**
     * The mods loaded and their versions, id@version, sorted: worldgen mods move biomes. Loom's "generated_" mods (the
     * dev run's libraries) are left out: their names change every launch.
     */
    static List<String> mods() {
        var mods = new TreeSet<String>();
        Platform.getMods().stream()
                .filter(mod -> !mod.getModId().startsWith("generated_"))
                .forEach(mod -> mods.add(mod.getModId() + "@" + mod.getVersion()));
        return List.copyOf(mods);
    }
}
