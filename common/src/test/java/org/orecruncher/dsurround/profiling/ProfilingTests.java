package org.orecruncher.dsurround.profiling;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.RenderTimers;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.mixinutils.OptionalHook;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for /dsprofile: its scenes are complete, its files are named and pruned as they should be, a recording is
 * written with nothing private in it, and nothing outside it needs the {@code jdk.jfr} module, which a player's Java may
 * not have.
 */
public class ProfilingTests {

    // The render timers belong to effects whose shaders take their ids from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @TempDir
    Path folder;

    private static JsonObject english() throws IOException {
        try (var in = Objects.requireNonNull(ProfilingTests.class.getResourceAsStream("/assets/dsurround/lang/en_us.json"))) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    // ---- The scenes and files

    @Test
    void everySceneSaysWhatItIs() throws IOException {
        var english = english();
        for (var scene : ProfileScene.values()) {
            assertTrue(english.has(scene.translationKey() + ".what"), scene.name());
            assertTrue(english.has(scene.translationKey() + ".tip"), scene.name());
            assertTrue(scene.settle() > 0, scene.name());
            for (var command : scene.commands())
                assertFalse(command.startsWith("/"), scene + ": " + command);
        }
    }

    @Test
    void theGeneralSceneChangesNothing() {
        // No settings held, no server commands: the game as it is
        assertTrue(ProfileScene.GENERAL.commands().isEmpty());
        assertDoesNotThrow(() -> ProfileScene.GENERAL.setUp().run());
    }

    @Test
    void filesAreNamedForTheirSceneAndTime() {
        assertEquals("dsurround-ground_fog-20261017-091502.jfr",
                ProfileFiles.name(ProfileScene.GROUND_FOG, LocalDateTime.of(2026, 10, 17, 9, 15, 2)));
    }

    @Test
    void onlyTheNewestAreKept() throws IOException {
        // Twelve zipped recordings a minute apart, the newest left unzipped, one recording and one zip left half
        // written, and a file of the player's own
        for (int i = 0; i < 13; i++) {
            var extension = i == 12 ? ".jfr" : ".zip";
            var file = Files.writeString(this.folder.resolve("dsurround-general-2026101709%02d00%s".formatted(i, extension)), "x");
            Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L + i * 60_000L));
        }
        Files.writeString(this.folder.resolve("dsurround-aurora-20261017-100000.jfr" + ProfileFiles.UNFINISHED), "x");
        Files.writeString(this.folder.resolve("dsurround-aurora-20261017-100000.zip" + ProfileFiles.UNFINISHED), "x");
        Files.writeString(this.folder.resolve("notes.txt"), "mine");

        var deleted = ProfileFiles.prune(this.folder, ProfileFiles.KEPT, new RecordingLog());

        assertEquals(5, deleted);
        try (Stream<Path> left = Files.list(this.folder)) {
            var names = left.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(ProfileFiles.KEPT + 1, names.size());
            assertTrue(names.contains("notes.txt"));
            assertTrue(names.contains("dsurround-general-20261017091200.jfr"), names.toString());
            // The oldest three went
            for (int i = 0; i < 3; i++)
                assertFalse(names.contains("dsurround-general-2026101709%02d00.zip".formatted(i)), names.toString());
            assertTrue(names.stream().noneMatch(n -> n.endsWith(ProfileFiles.UNFINISHED)));
        }
    }

    @Test
    void aRecordingIsZipped() throws IOException {
        var recording = this.folder.resolve(ProfileFiles.name(ProfileScene.FROST, LocalDateTime.of(2026, 10, 17, 11, 18, 31)));
        var random = Randomizer.create(7);
        var content = new byte[100_000];
        for (int i = 0; i < content.length; i++)
            content[i] = (byte) random.nextInt(256);
        Files.write(recording, content);

        var zip = ProfileFiles.zip(recording);

        assertEquals("dsurround-frost-20261017-111831.zip", zip.getFileName().toString());
        assertFalse(Files.exists(recording), "the recording is left beside its zip");
        try (Stream<Path> left = Files.list(this.folder)) {
            assertEquals(List.of(zip), left.toList());
        }
        try (var in = new ZipInputStream(Files.newInputStream(zip))) {
            var entry = in.getNextEntry();
            assertNotNull(entry);
            assertEquals("dsurround-frost-20261017-111831.jfr", entry.getName());
            assertArrayEquals(content, in.readAllBytes());
            assertNull(in.getNextEntry(), "one file in it");
        }
    }

    @Test
    void aMissingFolderIsNothingToPrune() {
        assertEquals(0, ProfileFiles.prune(this.folder.resolve("absent"), ProfileFiles.KEPT, new RecordingLog()));
    }

    @Test
    void theTimersAreNamedOnce() {
        var names = new HashSet<String>();
        for (var timer : RenderTimers.ALL)
            assertTrue(names.add(timer.name()), "twice: " + timer.name());
    }

    // ---- Recording

    @Test
    void flightRecorderIsHere() {
        // The JDK the tests run on has it; a player's Java may not, which isAvailable reports rather than fails on
        assertTrue(Profiler.isAvailable());
    }

    // As the render thread captures it in the game, which tests can't: there is no OpenGL here
    static ProfileEnvironment environment() {
        return new ProfileEnvironment("Dynamic Surroundings 26.2-0.4.7 (Fabric)", 42, "Test GPU/PCIe/SSE2", "Test Vendor",
                "discrete", "4.6.0 Test Driver 1.2.3", "OpenGL", "fabulous, improved transparency on", false, 12,
                ProfileEnvironment.javaRuntime(), ProfileEnvironment.operatingSystem(),
                List.of(new ProfileFeature(ProfileFeature.HOOK, "DYNAMIC_CLOUDS", "MISSING", false),
                        new ProfileFeature(ProfileFeature.SETTING, "weatherOptions.enableDustStorms", "true", true)));
    }

    private static List<ProfileFeature> features(Configuration config) {
        return ProfileFeature.of(config,
                hook -> hook == OptionalHook.DYNAMIC_CLOUDS ? OptionalHook.Status.MISSING : OptionalHook.Status.APPLIED,
                conflict -> conflict == FeatureConflict.PARTICLE_WEATHER ? Optional.of("particlerain") : Optional.empty());
    }

    private static ProfileFeature named(List<ProfileFeature> features, String name) {
        return features.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    @Test
    void theFeaturesSayWhatIsOffAndWhatWasChanged() {
        var config = new Configuration();
        config.weatherOptions.enableDustStorms = false;
        config.entityEffects.entityEffectRange = 16;
        var features = features(config);

        assertEquals(new ProfileFeature(ProfileFeature.HOOK, "DYNAMIC_CLOUDS", "MISSING", false), named(features, "DYNAMIC_CLOUDS"));
        assertEquals(new ProfileFeature(ProfileFeature.HOOK, "LIGHTNING_BOLTS", "APPLIED", true), named(features, "LIGHTNING_BOLTS"));
        assertEquals(new ProfileFeature(ProfileFeature.CONFLICT, "PARTICLE_WEATHER", "off (particlerain installed)", false),
                named(features, "PARTICLE_WEATHER"));
        assertEquals(new ProfileFeature(ProfileFeature.CONFLICT, "RAIN_RIPPLES", "on", true), named(features, "RAIN_RIPPLES"));
        assertEquals(new ProfileFeature(ProfileFeature.SETTING, "weatherOptions.enableDustStorms", "false", false),
                named(features, "weatherOptions.enableDustStorms"));
        assertEquals(new ProfileFeature(ProfileFeature.SETTING, "entityEffects.entityEffectRange", "16", false),
                named(features, "entityEffects.entityEffectRange"));
        assertTrue(named(features, "weatherOptions.enableDynamicClouds").usual());
        // Every setting is named by its group, and every hook and conflict is there
        assertTrue(features.stream().filter(f -> f.kind().equals(ProfileFeature.SETTING)).allMatch(f -> f.name().contains(".")));
        assertEquals(OptionalHook.values().length, features.stream().filter(f -> f.kind().equals(ProfileFeature.HOOK)).count());
        assertEquals(FeatureConflict.values().length, features.stream().filter(f -> f.kind().equals(ProfileFeature.CONFLICT)).count());
    }

    @Test
    void theFeaturesHoldNoText() {
        // A text setting could hold anything a player typed: only switches, numbers and choices are written
        for (var feature : features(new Configuration()))
            if (feature.kind().equals(ProfileFeature.SETTING))
                assertTrue(feature.state().matches("true|false|-?[\\d.E-]+|[A-Z0-9_]+"), feature.name() + " = " + feature.state());
    }

    @Test
    void aRecordingIsWrittenWithNothingPrivate() throws Exception {
        var file = this.folder.resolve("test.jfr");
        ProfileRecording.record(file, Duration.ofSeconds(2), environment());
        assertTrue(Files.size(file) > 0);
        assertFalse(Files.exists(this.folder.resolve("test.jfr" + ProfileFiles.UNFINISHED)), "the unfinished file is left");

        try (var recording = new RecordingFile(file)) {
            // The mod's event is there, and none of the private ones
            var types = recording.readEventTypes();
            assertTrue(types.stream().anyMatch(type -> type.getName().equals("dsurround.RenderTimer")));
            while (recording.hasMoreEvents()) {
                var name = recording.readEvent().getEventType().getName();
                assertFalse(ProfileRecording.PRIVATE.contains(name), "recorded " + name);
            }
        }

        // Nor anything that names the player's account, folders or machine, anywhere in the file
        var content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        var userName = System.getProperty("user.name");
        var personal = Stream.of(userName, System.getProperty("user.home"), System.getenv("COMPUTERNAME"),
                        System.getenv("HOSTNAME"), System.getenv("USERNAME"))
                .filter(Objects::nonNull).filter(s -> s.length() >= 3).toList();
        for (var text : personal)
            assertFalse(content.contains(text), "the recording holds \"" + text + "\"");
    }

    @Test
    void aRecordingSaysWhatItWasMadeOn() throws Exception {
        var file = this.folder.resolve("test.jfr");
        var environment = environment();
        ProfileRecording.record(file, Duration.ofSeconds(1), environment);

        var events = RecordingFile.readAllEvents(file).stream()
                .filter(e -> e.getEventType().getName().equals("dsurround.Environment"))
                .toList();
        assertEquals(1, events.size(), "written once");
        var event = events.getFirst();
        assertEquals(environment.mod(), event.getString("mod"));
        assertEquals(environment.mods(), event.getInt("mods"));
        assertEquals(environment.gpu(), event.getString("gpu"));
        assertEquals(environment.gpuVendor(), event.getString("gpuVendor"));
        assertEquals(environment.gpuType(), event.getString("gpuType"));
        assertEquals(environment.driver(), event.getString("driver"));
        assertEquals(environment.backend(), event.getString("backend"));
        assertEquals(environment.graphics(), event.getString("graphics"));
        assertEquals(environment.shaderPack(), event.getBoolean("shaderPack"));
        assertEquals(environment.renderDistance(), event.getInt("renderDistance"));
        assertEquals(environment.java(), event.getString("java"));
        assertEquals(environment.os(), event.getString("os"));
    }

    @Test
    void aRecordingHasTheHandlersTicks() throws Exception {
        var file = this.folder.resolve("test.jfr");
        var recorder = new Thread(() -> {
            try {
                ProfileRecording.record(file, Duration.ofSeconds(1), environment());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        recorder.start();
        // Ticks as the client thread would, until the recording is done; some fall before it starts, and are dropped
        long tick = 0;
        while (recorder.isAlive()) {
            HandlerTicks.ticked(++tick, "Precipitation Handler", 1_500_000L, true);
            HandlerTicks.ticked(tick, "Aurora Handler", 2_000L, false);
            Thread.sleep(50);
        }
        recorder.join();
        long last = tick;

        var events = RecordingFile.readAllEvents(file).stream()
                .filter(e -> e.getEventType().getName().equals("dsurround.HandlerTick"))
                .toList();
        assertFalse(events.isEmpty());
        for (var event : events) {
            var handler = event.getString("handler");
            assertTrue(handler.equals("Precipitation Handler") || handler.equals("Aurora Handler"), handler);
            var precipitation = handler.equals("Precipitation Handler");
            assertEquals(precipitation ? 1_500_000L : 2_000L, event.getLong("time"));
            assertEquals(precipitation, event.getBoolean("ran"));
            assertTrue(event.getLong("tick") > 0 && event.getLong("tick") <= last);
        }
    }

    @Test
    void aRecordingHasTheFeatures() throws Exception {
        var file = this.folder.resolve("test.jfr");
        var environment = environment();
        ProfileRecording.record(file, Duration.ofSeconds(1), environment);

        var events = RecordingFile.readAllEvents(file).stream()
                .filter(e -> e.getEventType().getName().equals("dsurround.Feature"))
                .map(e -> new ProfileFeature(e.getString("kind"), e.getString("name"), e.getString("state"), e.getBoolean("usual")))
                .toList();
        assertEquals(environment.features(), events);
    }

    @Test
    void theEnvironmentNamesNoOne() {
        // Of the system, only Java's and the operating system's versions: nothing of the account, its folders or the
        // machine's name
        var system = ProfileEnvironment.javaRuntime() + " " + ProfileEnvironment.operatingSystem();
        var personal = Stream.of(System.getProperty("user.name"), System.getProperty("user.home"), System.getenv("COMPUTERNAME"),
                        System.getenv("HOSTNAME"), System.getenv("USERNAME"))
                .filter(Objects::nonNull).filter(s -> s.length() >= 3).toList();
        for (var text : personal)
            assertFalse(system.contains(text), "the environment holds \"" + text + "\"");
    }

    @Test
    void onlyTheRecordingUsesJfr() throws IOException {
        // Anywhere else, a class using jdk.jfr would fail to load in a Java without that module, and could take the
        // game down with it
        var sources = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sources)) {
            var users = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("import jdk.jfr");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
            assertEquals(List.of("EnvironmentEvent.java", "FeatureEvent.java", "HandlerTickEvent.java", "ProfileRecording.java", "RenderTimerEvent.java"), users);
        }
    }
}
