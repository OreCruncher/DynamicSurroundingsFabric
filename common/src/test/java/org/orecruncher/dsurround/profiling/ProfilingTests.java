package org.orecruncher.dsurround.profiling;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orecruncher.dsurround.effects.RenderTimers;
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
import java.util.stream.Stream;

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
        // Thirteen recordings a minute apart, one left half written, and a file of the player's own
        for (int i = 0; i < 13; i++) {
            var file = Files.writeString(this.folder.resolve("dsurround-general-2026101709%02d00.jfr".formatted(i)), "x");
            Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L + i * 60_000L));
        }
        Files.writeString(this.folder.resolve("dsurround-aurora-20261017-100000.jfr" + ProfileFiles.UNFINISHED), "x");
        Files.writeString(this.folder.resolve("notes.txt"), "mine");

        var deleted = ProfileFiles.prune(this.folder, ProfileFiles.KEPT, new RecordingLog());

        assertEquals(4, deleted);
        try (Stream<Path> left = Files.list(this.folder)) {
            var names = left.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(ProfileFiles.KEPT + 1, names.size());
            assertTrue(names.contains("notes.txt"));
            // The oldest three went
            for (int i = 0; i < 3; i++)
                assertFalse(names.contains("dsurround-general-2026101709%02d00.jfr".formatted(i)), names.toString());
            assertTrue(names.stream().noneMatch(n -> n.endsWith(ProfileFiles.UNFINISHED)));
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

    @Test
    void aRecordingIsWrittenWithNothingPrivate() throws Exception {
        var file = this.folder.resolve("test.jfr");
        ProfileRecording.record(file, Duration.ofSeconds(2));
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
            assertEquals(List.of("ProfileRecording.java", "RenderTimerEvent.java"), users);
        }
    }
}
