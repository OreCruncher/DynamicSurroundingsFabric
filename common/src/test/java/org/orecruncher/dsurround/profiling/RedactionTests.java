// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the player's home folder is overwritten in a recording, in every form and encoding it may be written in,
 * without the file changing its length or becoming unreadable.
 */
public class RedactionTests {

    @TempDir
    Path folder;

    // A character array as JFR writes one: each character a variable-length number, seven bits a byte
    private static byte[] varints(String text) {
        var out = new ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            while (c >= 0x80) {
                out.write((c & 0x7F) | 0x80);
                c >>>= 7;
            }
            out.write(c);
        }
        return out.toByteArray();
    }

    private static int count(byte[] data, byte[] find) {
        int n = 0;
        outer:
        for (int i = 0; i + find.length <= data.length; i++) {
            for (int j = 0; j < find.length; j++)
                if (data[i + j] != find[j])
                    continue outer;
            n++;
        }
        return n;
    }

    @Test
    void theAccountsNameIsStarredInEachForm() {
        var forms = Redaction.replacements("C:\\Users\\tedjk\\");
        assertEquals("C:\\Users\\*****", forms.get("C:\\Users\\tedjk"));
        assertEquals("C:/Users/*****", forms.get("C:/Users/tedjk"));
        var spaced = Redaction.replacements("/home/Jo Smith");
        assertEquals("/home/** *****", spaced.get("/home/Jo Smith"));
        assertEquals("/home/**%20*****", spaced.get("/home/Jo%20Smith"));
    }

    @Test
    void aHomeThatNamesNoOneIsLeft() {
        assertTrue(Redaction.replacements(null).isEmpty());
        assertTrue(Redaction.replacements("/").isEmpty());
        assertTrue(Redaction.replacements("/u").isEmpty());
    }

    @Test
    void aStandInTakesAsManyBytesInEachEncoding() {
        // ASCII, Latin-1, Latin Extended, CJK low and high
        for (var name : new String[]{"tedjk", "José", "Łukasz", "Ἀλέξ", "विजय", "明美", "한국"}) {
            var stars = Redaction.star(name);
            assertEquals(name.getBytes(StandardCharsets.UTF_8).length, stars.getBytes(StandardCharsets.UTF_8).length, name);
            assertEquals(varints(name).length, varints(stars).length, name);
        }
    }

    @Test
    void everyEncodingIsOverwritten() {
        var home = "C:/Users/José";
        var url = "file:///" + home + "/game/mods/dsurround.jar";
        var latin = url.getBytes(StandardCharsets.ISO_8859_1);
        var utf8 = url.getBytes(StandardCharsets.UTF_8);
        var chars = varints(url);
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{1, 2, 3});
        out.writeBytes(latin);
        out.writeBytes(utf8);
        out.writeBytes(chars);
        var data = out.toByteArray();
        int length = data.length;

        assertEquals(3, Redaction.redact(data, Redaction.replacements(home)));
        assertEquals(length, data.length);
        assertEquals(0, count(data, home.getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(0, count(data, home.getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, count(data, varints(home)));
        // The rest of the path is left
        assertEquals(3, count(data, "/game/mods/dsurround.jar".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void aRedactedRecordingStillReads() throws Exception {
        // Overwrite something certain to be in any recording, a module's location, and read every event back
        var file = this.folder.resolve("test.jfr");
        ProfileRecording.record(file, Duration.ofSeconds(1), ProfilingTests.environment());
        var before = Files.size(file);
        Redaction.redact(file, "jrt:/java.base");
        assertEquals(before, Files.size(file));
        assertFalse(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains("jrt:/java.base"));
        int events = 0;
        try (var recording = new RecordingFile(file)) {
            while (recording.hasMoreEvents()) {
                recording.readEvent();
                events++;
            }
        }
        assertTrue(events > 0);
    }

    @Test
    void aCodeLocationsFolderIsFoundInEachScheme() {
        assertEquals("C:/game/mods", Redaction.folderOf(URI.create("union:/C:/game/mods/a.jar%23205!/")));
        assertEquals("C:/Games/My%20Pack/mods", Redaction.folderOf(URI.create("file:///C:/Games/My%20Pack/mods/sodium.jar")));
        assertEquals("home/bob/.minecraft/mods", Redaction.folderOf(URI.create("jar:file:///home/bob/.minecraft/mods/b.jar!/")));
        assertNull(Redaction.folderOf(URI.create("jrt:/java.base")));
    }

    @Test
    void theGamesFolderIsStarredLeavingTheJarsName() {
        // A game kept outside the home folder, the pack's name in its path (as NeoForge's module locations give it)
        var url = "file:///D:/Games/Instances/Bob's SMP Pack/mods/sodium.jar";
        var data = url.getBytes(StandardCharsets.UTF_8);
        var replacements = Redaction.replacements("C:\\Users\\bob", List.of("D:\\Games\\Instances\\Bob's SMP Pack"));
        assertTrue(Redaction.redact(data, replacements) > 0);
        // Starred whole, its separators too: not even how deep it is or how long its names are is left
        assertEquals("file:///" + "*".repeat("D:/Games/Instances/Bob's SMP Pack".length()) + "/mods/sodium.jar", new String(data, StandardCharsets.UTF_8));
        // The percent-encoded form, as a URL writes it
        var encoded = "file:///D:/Games/Instances/Bob's%20SMP%20Pack/mods/sodium.jar".getBytes(StandardCharsets.UTF_8);
        Redaction.redact(encoded, replacements);
        assertFalse(new String(encoded, StandardCharsets.UTF_8).contains("SMP"));
    }

    @Test
    void aFolderInsideAnotherIsRedactedWhole() {
        // The game's folder inside the home folder: its own parts are starred, not only the account's name
        var replacements = Redaction.replacements("C:\\Users\\bob", List.of("C:\\Users\\bob\\curseforge\\My Pack"));
        var data = "C:/Users/bob/curseforge/My Pack/mods/a.jar".getBytes(StandardCharsets.UTF_8);
        Redaction.redact(data, replacements);
        assertEquals("*".repeat("C:/Users/bob/curseforge/My Pack".length()) + "/mods/a.jar", new String(data, StandardCharsets.UTF_8));
    }

    @Test
    void aHomeFolderWrittenAnotherWayIsStarred() {
        for (var path : new String[]{"C:/Users/JOHNSM~1/AppData/Local/Temp/1.jar", "c:\\users\\bob\\game\\a.jar",
                "file:///home/Jos%C3%A9/game/a.jar", "C:\\USERS\\Bob\\x"}) {
            var data = path.getBytes(StandardCharsets.UTF_8);
            assertEquals(1, Redaction.redactHomes(data), path);
            var redacted = new String(data, StandardCharsets.UTF_8);
            assertFalse(redacted.contains("JOHNSM") || redacted.contains("bob") || redacted.contains("Bob") || redacted.contains("Jos"), redacted);
            assertEquals(path.length(), redacted.length());
        }
        // Only the name: the folders past it are left, and so is a path with no home folder in it
        var data = "C:/Users/bob/game/a.jar".getBytes(StandardCharsets.UTF_8);
        Redaction.redactHomes(data);
        assertEquals("C:/Users/***/game/a.jar", new String(data, StandardCharsets.UTF_8));
        assertEquals(0, Redaction.redactHomes("D:/Games/users.txt".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theGamesFolderIsPrivate() {
        assertTrue(Redaction.privateFolders().contains(System.getProperty("user.dir")));
    }
}
