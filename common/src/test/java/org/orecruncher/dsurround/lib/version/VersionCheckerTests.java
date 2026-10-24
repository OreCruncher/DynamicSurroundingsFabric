package org.orecruncher.dsurround.lib.version;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.logging.IModLog.Level;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for reading the version information within its size limit.
 */
public class VersionCheckerTests {

    private static ByteArrayInputStream bytes(int count) {
        return new ByteArrayInputStream("x".repeat(count).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsSmallInformationQuietly() throws IOException {
        var log = new RecordingLog();
        var text = VersionChecker.read("test", new ByteArrayInputStream("{\"a\":1}".getBytes(StandardCharsets.UTF_8)), log);
        assertEquals("{\"a\":1}", text);
        assertTrue(log.entries.isEmpty());
    }

    @Test
    public void justUnderTheWarningIsQuiet() throws IOException {
        var log = new RecordingLog();
        assertEquals(VersionChecker.WARN_BYTES - 1, VersionChecker.read("test", bytes(VersionChecker.WARN_BYTES - 1), log).length());
        assertTrue(log.entries.isEmpty());
    }

    @Test
    public void warnsNearTheLimit() throws IOException {
        var log = new RecordingLog();
        assertEquals(VersionChecker.WARN_BYTES, VersionChecker.read("test", bytes(VersionChecker.WARN_BYTES), log).length());
        assertEquals(1, log.at(Level.WARN).size());
    }

    @Test
    public void readsExactlyTheLimit() throws IOException {
        var log = new RecordingLog();
        assertEquals(VersionChecker.MAX_BYTES, VersionChecker.read("test", bytes(VersionChecker.MAX_BYTES), log).length());
        assertEquals(1, log.at(Level.WARN).size());
    }

    @Test
    public void refusesMoreThanTheLimit() {
        var log = new RecordingLog();
        var e = assertThrows(VersionCheckException.class, () -> VersionChecker.read("test", bytes(VersionChecker.MAX_BYTES + 1), log));
        assertTrue(e.getMessage().contains(String.valueOf(VersionChecker.MAX_BYTES)));
    }
}
