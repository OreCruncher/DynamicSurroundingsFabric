// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.bench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A bench scenario: the world to run in, the scenes, and the runs, each a scene recorded with some settings. Read from
 * a JSON file (comments allowed); see {@code scripts/bench/README.md} for the format.
 *
 * @param name       the file's name without ".json", for the results folder
 * @param world      the world's folder name in saves; made if missing
 * @param seed       the seed to make it with, if missing
 * @param setup      commands run once the world is open (gamerules, game mode)
 * @param warmup     seconds to wait once the scene is drawn, before recording starts
 * @param record     seconds to record each run
 * @param repeats    how many times the runs are made, every other time in reverse order (A B, B A...), so a drift over
 *                   the session doesn't look like a setting's cost
 * @param vsync      vsync to set for the session, if given
 * @param maxFps     the frame rate limit to set for the session, if given (260 is none)
 * @param width      the window's width to set for the session, if given, and {@code height} its height: what a GPU
 *                   draws scales with the pixels, so runs to compare need the same size
 * @param screenshot whether each run takes a screenshot as it starts recording
 * @param volume     the master volume to set for the session, 0 to 1, if given: a sound whose volume works out to 0 isn't
 *                   played at all, so isn't processed either
 * @param scenes     the scenes, by name
 * @param runs       the runs, in order
 */
public record Scenario(String name, String world, OptionalLong seed, List<String> setup, int warmup, int record, int repeats,
                       @Nullable Boolean vsync, OptionalInt maxFps, OptionalInt width, OptionalInt height, boolean screenshot, OptionalDouble volume, Map<String, Scene> scenes, List<Run> runs) {

    /**
     * Where a run is recorded.
     *
     * @param biome    the biome to find the scene in, if it isn't remembered (marked with /dsbench mark, or found before)
     * @param yaw      where to look, if given; otherwise toward the most of the biome
     * @param pitch    where to look up and down, if given
     * @param commands commands run once the player is there (time, weather, /dsweather)
     * @param settledCommands commands run as the warm-up starts, as {@code commands} are: once what those set off has
     *                 finished, such as gravel falling into a cleared stage
     * @param profile  the /dsprofile scene to record with
     * @param shotCommands commands run just before the screenshot (a strike of lightning to catch in it), as the
     *                 player would type them, as {@code commands} are
     * @param shotDelay ticks from those commands to the screenshot, for what they start to show
     * @param sounds   sounds played around the player from the end of the commands to the end of the recording
     */
    public record Scene(@Nullable String biome, @Nullable Float yaw, @Nullable Float pitch, List<String> commands,
                        List<String> settledCommands, String profile, List<String> shotCommands, int shotDelay, List<Sounds> sounds) {
    }

    /**
     * A sound a scene plays around the player, at random places in a shell around where they stood when it began.
     *
     * @param id        the sound event
     * @param perSecond one-shots played a second, if more than 0
     * @param count     loops kept playing, if more than 0 (a sound made to loop: a beacon's hum)
     * @param minRadius the shell's inside, in blocks from the player's eyes
     * @param radius    the shell's outside
     * @param volume    each sound's volume
     * @param pitch     each sound's pitch
     * @param seed      where the places and variants come from, the same each run
     */
    public record Sounds(String id, double perSecond, int count, double minRadius, double radius, float volume, float pitch,
                         long seed) {
    }

    /**
     * One recording.
     *
     * @param label    its name in the results
     * @param scene    the scene's name
     * @param settings config values for it (dotted paths, as presets have): the setting sets it uses, in order, then its
     *                 own; every other run's are put back first
     */
    public record Run(String label, String scene, Map<String, JsonElement> settings) {
    }

    public static Scenario read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            var name = file.getFileName().toString().replaceFirst("\\.json$", "");
            return parse(name, JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    static Scenario parse(String name, JsonObject json) {
        var scenes = new LinkedHashMap<String, Scene>();
        var sceneJson = json.has("scenes") ? json.getAsJsonObject("scenes") : new JsonObject();
        for (var entry : sceneJson.entrySet()) {
            var s = entry.getValue().getAsJsonObject();
            scenes.put(entry.getKey(), new Scene(
                    s.has("biome") ? s.get("biome").getAsString() : null,
                    s.has("yaw") ? s.get("yaw").getAsFloat() : null,
                    s.has("pitch") ? s.get("pitch").getAsFloat() : null,
                    strings(s, "commands"),
                    strings(s, "settledCommands"),
                    s.has("profile") ? s.get("profile").getAsString() : "general",
                    strings(s, "shotCommands"),
                    s.has("shotDelay") ? Math.max(0, s.get("shotDelay").getAsInt()) : 0,
                    sounds(s)));
        }

        // Named groups of settings, for runs to share ("use"): such as every effect turned off
        var sets = new LinkedHashMap<String, JsonObject>();
        if (json.has("settingSets"))
            for (var entry : json.getAsJsonObject("settingSets").entrySet())
                sets.put(entry.getKey(), entry.getValue().getAsJsonObject());

        var runs = new ArrayList<Run>();
        if (json.has("runs")) {
            for (var element : json.getAsJsonArray("runs")) {
                var r = element.getAsJsonObject();
                var scene = r.get("scene").getAsString();
                if (!scenes.containsKey(scene))
                    throw new IllegalArgumentException("run names scene '" + scene + "', which isn't in scenes");
                var settings = new LinkedHashMap<String, JsonElement>();
                if (r.has("use")) {
                    var use = r.get("use");
                    var names = new ArrayList<String>();
                    if (use.isJsonArray())
                        use.getAsJsonArray().forEach(n -> names.add(n.getAsString()));
                    else
                        names.add(use.getAsString());
                    for (var setName : names) {
                        var set = sets.get(setName);
                        if (set == null)
                            throw new IllegalArgumentException("run uses setting set '" + setName + "', which isn't in settingSets");
                        for (var setting : set.entrySet())
                            settings.put(setting.getKey(), setting.getValue());
                    }
                }
                if (r.has("settings"))
                    for (var setting : r.getAsJsonObject("settings").entrySet())
                        settings.put(setting.getKey(), setting.getValue());
                runs.add(new Run(r.has("label") ? r.get("label").getAsString() : scene + "-" + runs.size(), scene, settings));
            }
        }
        if (runs.isEmpty())
            throw new IllegalArgumentException("a scenario needs runs");

        var video = json.has("video") ? json.getAsJsonObject("video") : new JsonObject();
        return new Scenario(name,
                json.has("world") ? json.get("world").getAsString() : "bench",
                json.has("seed") ? OptionalLong.of(json.get("seed").getAsLong()) : OptionalLong.empty(),
                strings(json, "setup"),
                json.has("warmup") ? json.get("warmup").getAsInt() : 15,
                json.has("record") ? json.get("record").getAsInt() : 60,
                json.has("repeats") ? Math.max(1, json.get("repeats").getAsInt()) : 1,
                video.has("vsync") ? video.get("vsync").getAsBoolean() : null,
                video.has("maxFps") ? OptionalInt.of(video.get("maxFps").getAsInt()) : OptionalInt.empty(),
                video.has("width") ? OptionalInt.of(video.get("width").getAsInt()) : OptionalInt.empty(),
                video.has("height") ? OptionalInt.of(video.get("height").getAsInt()) : OptionalInt.empty(),
                !json.has("screenshots") || json.get("screenshots").getAsBoolean(),
                json.has("volume") ? OptionalDouble.of(json.get("volume").getAsDouble()) : OptionalDouble.empty(),
                scenes, runs);
    }

    /**
     * Every run in the order they are made: the runs, repeated, every other time reversed
     */
    public List<Run> order() {
        var order = new ArrayList<Run>();
        for (int i = 0; i < this.repeats; i++) {
            var pass = new ArrayList<>(this.runs);
            if (i % 2 == 1)
                Collections.reverse(pass);
            order.addAll(pass);
        }
        return order;
    }

    private static List<Sounds> sounds(JsonObject scene) {
        var list = new ArrayList<Sounds>();
        if (!scene.has("sounds"))
            return list;
        for (var element : scene.getAsJsonArray("sounds")) {
            var o = element.getAsJsonObject();
            if (!o.has("id"))
                throw new IllegalArgumentException("a scene's sound needs an id");
            var sound = new Sounds(
                    o.get("id").getAsString(),
                    o.has("perSecond") ? o.get("perSecond").getAsDouble() : 0,
                    o.has("count") ? o.get("count").getAsInt() : 0,
                    o.has("minRadius") ? o.get("minRadius").getAsDouble() : 0,
                    o.has("radius") ? o.get("radius").getAsDouble() : 8,
                    o.has("volume") ? o.get("volume").getAsFloat() : 1,
                    o.has("pitch") ? o.get("pitch").getAsFloat() : 1,
                    o.has("seed") ? o.get("seed").getAsLong() : 1);
            if (sound.perSecond() <= 0 && sound.count() <= 0)
                throw new IllegalArgumentException("sound " + sound.id() + " needs a perSecond or a count");
            if (sound.minRadius() < 0 || sound.radius() < sound.minRadius())
                throw new IllegalArgumentException("sound " + sound.id() + " needs 0 <= minRadius <= radius");
            list.add(sound);
        }
        return list;
    }

    private static List<String> strings(JsonObject json, String member) {
        var list = new ArrayList<String>();
        if (json.has(member))
            for (var element : json.getAsJsonArray(member))
                list.add(element.getAsString());
        return list;
    }
}
