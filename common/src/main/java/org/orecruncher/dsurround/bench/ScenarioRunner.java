package org.orecruncher.dsurround.bench;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigPreset;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.profiling.ProfileScene;
import org.orecruncher.dsurround.profiling.Profiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Runs a {@link Scenario} in a development client, then closes the game: opens (or makes) its world, and for each run
 * applies its settings, goes to its scene (found by biome, or remembered in the world), runs the scene's commands,
 * waits for the terrain to be drawn and a warm-up, takes a screenshot and records a profile (/dsprofile). Everything
 * goes in {@code bench-results/<scenario>-<time>/} in the game's folder, with a {@code manifest.json} saying what each
 * run was, and {@code bench-results/latest.txt} names that folder. Stops at the first failure, writing the manifest
 * with it, and closes the game whatever happens.
 * <p>
 * Driven by the client tick, one step at a time; nothing here blocks the game. Commands are run as the player would
 * type them: the built-in server's (time, weather, gamerule) on the server with its full permission, so the world
 * needn't allow cheats; the mod's own (/dsweather) on the client.
 */
public final class ScenarioRunner {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    // How long each wait may take before the run is given up
    private static final int OPEN_SECONDS = 300;
    private static final int FIND_SECONDS = 300;
    private static final int ARRIVE_SECONDS = 60;
    private static final int DRAWN_SECONDS = 90;
    // At least this long after arriving before the terrain is taken as drawn: it starts out with nothing to draw
    private static final int SETTLE_SECONDS = 3;
    private static final int TICKS_PER_SECOND = 20;
    private static final BlockPos FIND_FROM = new BlockPos(0, 64, 0);

    private final Scenario scenario;
    private final Path results;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final JsonObject manifest = new JsonObject();
    private final JsonArray runResults = new JsonArray();
    private final Map<String, SceneLocations.Location> sessionScenes = new HashMap<>();
    private @Nullable Step current;
    private int stepTicks;
    private boolean finished;
    private @Nullable SceneLocations locations;
    private @Nullable ConfigPreset baseline;

    private ScenarioRunner(Scenario scenario, Path results) {
        this.scenario = scenario;
        this.results = results;
    }

    /**
     * One thing to do, ticked until it is done
     *
     * @param tick     does it, or checks; true once done
     * @param timeout  ticks it may take, 0 for one go
     */
    private record Step(String name, BooleanSupplier tick, int timeout) {
    }

    static void start(Path file) {
        var mc = Minecraft.getInstance();
        var gameFolder = mc.gameDirectory.toPath();
        var resultsRoot = gameFolder.resolve("bench-results");
        Scenario scenario;
        try {
            scenario = Scenario.read(file);
        } catch (Exception e) {
            Library.LOGGER.error(e, "Bench: unable to read the scenario %s", file);
            writeFailure(resultsRoot, file, e);
            mc.execute(mc::stop);
            return;
        }
        var results = resultsRoot.resolve(scenario.name() + "-" + LocalDateTime.now().format(STAMP));
        var runner = new ScenarioRunner(scenario, results);
        runner.begin(file);
        ClientTickEvent.CLIENT_POST.register(m -> runner.tick());
    }

    private void begin(Path file) {
        try {
            Files.createDirectories(this.results);
            Files.writeString(this.results.getParent().resolve("latest.txt"), this.results.toAbsolutePath().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Library.LOGGER.error(e, "Bench: unable to make the results folder %s", this.results);
        }
        this.manifest.addProperty("scenario", file.toAbsolutePath().toString());
        this.manifest.addProperty("started", LocalDateTime.now().toString());
        this.manifest.addProperty("world", this.scenario.world());
        this.manifest.add("runs", this.runResults);
        this.manifest.addProperty("finished", false);
        Library.LOGGER.info("Bench: running %s, results in %s", file, this.results);

        this.step("open the world", this::openWorld, 0);
        this.step("wait for the world", () -> {
            var mc = Minecraft.getInstance();
            return mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.gui.screen() == null;
        }, OPEN_SECONDS * TICKS_PER_SECOND);
        this.step("set up the session", this::setUpSession, 0);
    }

    private void step(String name, BooleanSupplier tick, int timeout) {
        this.steps.add(new Step(name, tick, timeout));
    }

    private void tick() {
        if (this.finished)
            return;
        try {
            if (this.current == null) {
                this.current = this.steps.poll();
                this.stepTicks = 0;
                if (this.current == null) {
                    this.finish(null);
                    return;
                }
                Library.LOGGER.info("Bench: %s", this.current.name());
            }
            this.stepTicks++;
            if (this.current.tick().getAsBoolean()) {
                this.current = null;
            } else if (this.current.timeout() > 0 && this.stepTicks > this.current.timeout()) {
                throw new IllegalStateException("timed out: " + this.current.name());
            }
        } catch (Exception e) {
            Library.LOGGER.error(e, "Bench: failed at %s", this.current == null ? "?" : this.current.name());
            this.finish(e);
        }
    }

    /**
     * Opens the scenario's world from the title screen, making it first if it isn't there; false until the title screen
     * shows (or the first launch's accessibility screen)
     */
    private boolean openWorld() {
        var mc = Minecraft.getInstance();
        // The first launch's accessibility screen, and pausing when the window loses focus, would hold a run up
        mc.options.onboardAccessibility = false;
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.overlay() != null || mc.level != null || mc.gui.screen() == null)
            return false;
        if (!(mc.gui.screen() instanceof TitleScreen) && !mc.gui.screen().getClass().getSimpleName().contains("Onboarding"))
            return false;
        mc.options.save();

        var name = this.scenario.world();
        if (mc.getLevelSource().levelExists(name)) {
            mc.createWorldOpenFlows().openWorld(name, () -> this.finish(new IllegalStateException("opening the world was cancelled")));
        } else {
            Library.LOGGER.info("Bench: making the world %s", name);
            var settings = new LevelSettings(name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
            var options = this.scenario.seed().isPresent()
                    ? new WorldOptions(this.scenario.seed().getAsLong(), true, false)
                    : WorldOptions.defaultWithRandomSeed();
            mc.createWorldOpenFlows().createFreshLevel(name, settings, options, WorldPresets::createNormalWorldDimensions, new TitleScreen());
        }
        return true;
    }

    private boolean setUpSession() {
        var mc = Minecraft.getInstance();
        var server = server();
        this.manifest.addProperty("seed", server.getWorldGenSettings().options().seed());
        try {
            this.locations = SceneLocations.load(server.getWorldPath(LevelResource.ROOT));
        } catch (IOException e) {
            throw new IllegalStateException("unable to read the world's scenes", e);
        }
        try {
            this.locations.noteModsIfUnknown(server.getWorldGenSettings().options().seed());
        } catch (IOException e) {
            Library.LOGGER.warn("Bench: unable to note the mods with the scenes: %s", e);
        }
        // The mods that differ from those the remembered scenes were found with: worldgen mods move biomes
        var modChanges = new JsonArray();
        this.locations.modChanges().forEach(modChanges::add);
        this.manifest.add("modChangesSinceScenes", modChanges);

        if (this.scenario.vsync() != null)
            mc.options.enableVsync().set(this.scenario.vsync());
        this.scenario.maxFps().ifPresent(fps -> mc.options.framerateLimit().set(fps));
        if (this.scenario.width().isPresent() && this.scenario.height().isPresent())
            mc.getWindow().setWindowed(this.scenario.width().getAsInt(), this.scenario.height().getAsInt());
        // Its hints would sit in every screenshot
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.save();
        for (var command : this.scenario.setup())
            this.command(command);

        // Every run's settings, checked before anything is recorded, and as they all are now, to put back before each
        var config = ContainerManager.resolve(Configuration.class);
        var all = new LinkedHashMap<String, JsonElement>();
        var problems = new ArrayList<String>();
        for (var run : this.scenario.runs()) {
            all.putAll(run.settings());
            new ConfigPreset(run.label(), null, null, false, run.settings()).validated(config, p -> true, problems::add);
        }
        if (!problems.isEmpty())
            throw new IllegalArgumentException("settings that can't be applied: " + String.join("; ", problems));
        this.baseline = new ConfigPreset("baseline", null, null, false, all).current(config);

        var order = this.scenario.order();
        for (int i = 0; i < order.size(); i++)
            this.queueRun(i, order.get(i));
        return true;
    }

    private void queueRun(int index, Scenario.Run run) {
        var scene = this.scenario.scenes().get(run.scene());
        var result = new JsonObject();
        var tag = String.format(Locale.ROOT, "%02d-%s", index + 1, run.label());
        result.addProperty("index", index + 1);
        result.addProperty("label", run.label());
        result.addProperty("scene", run.scene());
        var settings = new JsonObject();
        run.settings().forEach(settings::add);
        result.add("settings", settings);

        var arrival = new Object() {
            @Nullable CompletableFuture<SceneLocations.Location> found;
            @Nullable SceneLocations.Location location;
            int shotTick;
            boolean shot;
            boolean recorded;
        };

        this.step(tag + ": settings", () -> {
            var config = ContainerManager.resolve(Configuration.class);
            var problems = config.applyPreset(this.baseline);
            problems += config.applyPreset(new ConfigPreset(run.label(), null, null, false, run.settings()));
            if (problems > 0)
                throw new IllegalStateException(problems + " settings didn't apply (see the log)");
            result.addProperty("started", LocalDateTime.now().toString());
            // In the manifest from the start, so a run that fails says how far it got
            this.runResults.add(result);
            return true;
        }, 0);
        this.step(tag + ": find the scene", () -> {
            if (arrival.found == null)
                arrival.found = this.sceneLocation(run.scene(), scene, result);
            if (!arrival.found.isDone())
                return false;
            arrival.location = arrival.found.join();
            result.add("location", locationJson(arrival.location));
            return true;
        }, FIND_SECONDS * TICKS_PER_SECOND);
        this.step(tag + ": go there", () -> {
            var l = arrival.location;
            float yaw = scene.yaw() != null ? scene.yaw() : l.yaw();
            float pitch = scene.pitch() != null ? scene.pitch() : l.pitch();
            this.serverCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.3f %.3f %.3f %.2f %.2f",
                    l.dimension(), l.x(), l.y(), l.z(), yaw, pitch));
            return true;
        }, 0);
        this.step(tag + ": arrive", () -> {
            var player = Minecraft.getInstance().player;
            var l = arrival.location;
            if (player == null || player.distanceToSqr(l.x(), l.y(), l.z()) > 4)
                return false;
            for (var command : scene.commands())
                this.command(command);
            return true;
        }, ARRIVE_SECONDS * TICKS_PER_SECOND);
        this.step(tag + ": wait for the terrain", () -> {
            if (this.stepTicks < SETTLE_SECONDS * TICKS_PER_SECOND)
                return false;
            if (Minecraft.getInstance().levelRenderer.hasRenderedAllSections())
                return true;
            if (this.stepTicks >= DRAWN_SECONDS * TICKS_PER_SECOND) {
                // Recorded anyway, and said so: some scenes keep sections changing (flowing water)
                result.addProperty("terrainNotSettled", true);
                return true;
            }
            return false;
        }, 0);
        this.step(tag + ": warm up", () -> this.stepTicks >= this.scenario.warmup() * TICKS_PER_SECOND, 0);
        this.step(tag + ": record", () -> {
            if (this.stepTicks == 1) {
                var profile = profileScene(scene.profile());
                var said = Profiler.start(profile, this.scenario.record(), zip -> {
                    arrival.recorded = true;
                    this.keepRecording(zip, tag, result);
                });
                if (Profiler.state() == Profiler.State.IDLE)
                    throw new IllegalStateException("the profile didn't start: " + said.stream().map(c -> c.getString()).toList());
                return false;
            }
            // The screenshot as recording starts, without the HUD (chat, the profile's indicator): hidden for a couple of
            // ticks, so a frame is drawn without it, then taken and the HUD shown again
            if (this.scenario.screenshot() && !arrival.shot && Profiler.state() == Profiler.State.RECORDING) {
                var hud = Minecraft.getInstance().gui.hud;
                if (arrival.shotTick == 0) {
                    arrival.shotTick = this.stepTicks;
                    if (!hud.isHidden())
                        hud.toggle();
                } else if (this.stepTicks >= arrival.shotTick + 2) {
                    this.screenshot(tag, result);
                    if (hud.isHidden())
                        hud.toggle();
                    arrival.shot = true;
                }
            }
            if (!arrival.recorded)
                return false;
            result.addProperty("finished", LocalDateTime.now().toString());
            this.writeManifest();
            return true;
        }, (this.scenario.record() + 120) * TICKS_PER_SECOND);
    }

    /**
     * Where a scene is: remembered this session, or in the world (checked that it is still in its biome, unless
     * marked), or found by its biome and remembered; on the server's thread
     */
    private CompletableFuture<SceneLocations.Location> sceneLocation(String name, Scenario.Scene scene, JsonObject result) {
        var known = this.sessionScenes.get(name);
        if (known != null)
            return CompletableFuture.completedFuture(known);
        var server = server();
        var remembered = this.locations.get(name);
        return server.submit(() -> {
            var level = server.overworld();
            if (remembered != null && (remembered.marked() || SceneFinder.stillThere(level, remembered))) {
                result.addProperty("how", remembered.marked() ? "marked" : "remembered");
                return remembered;
            }
            if (scene.biome() == null)
                throw new IllegalStateException("scene '" + name + "' has no biome to find it by: mark it with /dsbench mark " + name);
            result.addProperty("how", remembered == null ? "found" : "found again (its biome had moved)");
            var found = SceneFinder.find(level, scene.biome(), FIND_FROM);
            try {
                this.locations.put(name, found, server.getWorldGenSettings().options().seed());
            } catch (IOException e) {
                Library.LOGGER.warn("Bench: unable to remember scene %s: %s", name, e);
            }
            return found;
        }).thenApply(location -> {
            this.sessionScenes.put(name, location);
            return location;
        });
    }

    private void keepRecording(@Nullable Path zip, String tag, JsonObject result) {
        if (zip == null) {
            result.addProperty("error", "the profile couldn't be made (see the log)");
            return;
        }
        try {
            var kept = this.results.resolve(tag + "-" + zip.getFileName());
            Files.move(zip, kept, StandardCopyOption.REPLACE_EXISTING);
            result.addProperty("recording", kept.getFileName().toString());
        } catch (IOException e) {
            result.addProperty("recording", zip.toAbsolutePath().toString());
        }
    }

    private void screenshot(String tag, JsonObject result) {
        var name = tag + ".png";
        // Written to <results>/screenshots
        Screenshot.grab(this.results.toFile(), name, Minecraft.getInstance().gameRenderer.mainRenderTarget(), 1, message -> {});
        result.addProperty("screenshot", "screenshots/" + name);
    }

    private static ProfileScene profileScene(String name) {
        for (var scene : ProfileScene.values())
            if (scene.commandName().equals(name))
                return scene;
        throw new IllegalArgumentException("no /dsprofile scene " + name);
    }

    /**
     * Runs a command as the player would type it: the server's on the server, with its full permission, as the
     * player and where they are; anything else (the mod's own) on the client
     */
    private void command(String command) {
        var text = command.startsWith("/") ? command.substring(1) : command;
        var root = text.split(" ", 2)[0];
        if (server().getCommands().getDispatcher().getRoot().getChild(root) != null)
            this.serverCommand("execute as @a at @s run " + text);
        else
            Minecraft.getInstance().player.connection.sendCommand(text);
    }

    private void serverCommand(String command) {
        var server = server();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static MinecraftServer server() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null)
            throw new IllegalStateException("no single player world open");
        return server;
    }

    private static JsonObject locationJson(SceneLocations.Location l) {
        var json = new JsonObject();
        json.addProperty("biome", l.biome());
        json.addProperty("dimension", l.dimension());
        var pos = new JsonArray();
        pos.add(l.x());
        pos.add(l.y());
        pos.add(l.z());
        json.add("pos", pos);
        json.addProperty("yaw", l.yaw());
        json.addProperty("pitch", l.pitch());
        json.addProperty("biomeShare", l.biomeShare());
        return json;
    }

    /**
     * Puts the settings back, writes the manifest and closes the game
     */
    private void finish(@Nullable Exception failure) {
        if (this.finished)
            return;
        this.finished = true;
        if (this.baseline != null) {
            try {
                ContainerManager.resolve(Configuration.class).applyPreset(this.baseline);
            } catch (Exception e) {
                Library.LOGGER.warn("Bench: unable to put the settings back: %s", e);
            }
        }
        if (failure != null)
            this.manifest.addProperty("error", (this.current == null ? "" : this.current.name() + ": ") + failure);
        this.manifest.addProperty("finished", failure == null);
        this.manifest.addProperty("ended", LocalDateTime.now().toString());
        this.writeManifest();
        Library.LOGGER.info("Bench: %s; results in %s", failure == null ? "done" : "failed", this.results);
        Minecraft.getInstance().stop();
    }

    private void writeManifest() {
        try {
            Files.writeString(this.results.resolve("manifest.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(this.manifest), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Library.LOGGER.error(e, "Bench: unable to write the manifest");
        }
    }

    /**
     * A manifest saying the scenario couldn't even be read, so whoever waits for one isn't left waiting
     */
    private static void writeFailure(Path resultsRoot, Path file, Exception e) {
        try {
            var folder = resultsRoot.resolve("failed-" + LocalDateTime.now().format(STAMP));
            Files.createDirectories(folder);
            var json = new JsonObject();
            json.addProperty("scenario", file.toAbsolutePath().toString());
            json.addProperty("finished", false);
            json.addProperty("error", "unable to read the scenario: " + e);
            Files.writeString(folder.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            Files.writeString(resultsRoot.resolve("latest.txt"), folder.toAbsolutePath().toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Nothing more can be done; the log has it
        }
    }
}
