package org.orecruncher.dsurround.lib.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModLog, logging to a recording SLF4J logger.
 */
public class ModLogTests {

    record Entry(Level level, String message, Throwable throwable) {
    }

    /**
     * Records each logging call, with the message as SLF4J would format it.
     */
    static final class RecordingLogger extends LegacyAbstractLogger {
        @Serial
        private static final long serialVersionUID = 1L;

        final List<Entry> entries = new ArrayList<>();

        RecordingLogger(String name) {
            this.name = name;
        }

        @Override
        protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
            this.entries.add(new Entry(level, MessageFormatter.basicArrayFormat(messagePattern, arguments), throwable));
        }

        @Override
        protected String getFullyQualifiedCallerName() {
            return null;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }
    }

    private RecordingLogger recorder;
    private ModLog log;

    @BeforeEach
    void setUp() {
        this.recorder = new RecordingLogger("dsurround");
        this.log = new ModLog(this.recorder);
        ModLog.setDebug(false);
        ModLog.setTraceMask(0);
    }

    @AfterEach
    void tearDown() {
        // The settings are global; leave them off for other tests
        ModLog.setDebug(false);
        ModLog.setTraceMask(0);
    }

    private List<String> messages() {
        return this.recorder.entries.stream().map(Entry::message).toList();
    }

    private Entry only() {
        assertEquals(1, this.recorder.entries.size(), this.recorder.entries.toString());
        return this.recorder.entries.getFirst();
    }

    // ---- Formatting ------------------------------------------------------------------------------------------

    @Test
    void messageIsFormattedWithArguments() {
        this.log.info("loaded %d of %s", 3, "blocks");

        assertEquals("loaded 3 of blocks", only().message());
        assertEquals(Level.INFO, only().level());
    }

    @Test
    void messageWithoutArgumentsIsLoggedAsIs() {
        this.log.info("100% done, %s untouched");

        assertEquals("100% done, %s untouched", only().message());
    }

    @Test
    void badFormatIsLoggedInsteadOfThrowing() {
        // Regression: String.format's exception went back to the caller
        assertDoesNotThrow(() -> this.log.warn("count %d", "not a number"));

        var message = only().message();
        assertTrue(message.startsWith("count %d [format error IllegalFormatConversionException"), message);
        assertTrue(message.contains("arguments [not a number]"), message);
        assertEquals(Level.WARN, only().level());
    }

    @Test
    void missingArgumentIsLoggedInsteadOfThrowing() {
        assertDoesNotThrow(() -> this.log.error(new RuntimeException("x"), "%s and %s", "one"));

        assertTrue(only().message().contains("MissingFormatArgumentException"), only().message());
    }

    @Test
    void argumentWhoseToStringThrowsIsHandled() {
        var bad = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("no");
            }
        };

        assertDoesNotThrow(() -> this.log.info("value %s", bad));

        var message = only().message();
        assertTrue(message.startsWith("value %s [format error IllegalStateException: no"), message);
        assertTrue(message.contains("1 argument(s)"), message);
    }

    @Test
    void formatHelper() {
        assertEquals("a", ModLog.format("a"));
        assertEquals("a", ModLog.format("a", (Object[]) null));
        assertEquals("x=1", ModLog.format("x=%d", 1));
    }

    // ---- Lines and exceptions --------------------------------------------------------------------------------

    @Test
    void eachLineIsItsOwnRecord() {
        this.log.info("first\nsecond\r\nthird");

        // \r\n is a line break too; no stray \r is left on "second"
        assertEquals(List.of("first", "second", "third"), this.messages());
    }

    @Test
    void exceptionIsLoggedWithTheLastLine() {
        // Regression: the exception was a separate "EXCEPTION:" record
        var e = new IllegalStateException("boom");
        this.log.error(e, "Unable to load %s\nUsing defaults", "thing");

        assertEquals(List.of("Unable to load thing", "Using defaults"), this.messages());
        assertNull(this.recorder.entries.get(0).throwable());
        assertSame(e, this.recorder.entries.get(1).throwable());
        assertTrue(this.recorder.entries.stream().allMatch(x -> x.level() == Level.ERROR));
    }

    @Test
    void emptyMessageStillLogsTheException() {
        var e = new IllegalStateException("boom");
        this.log.error(e, "");

        assertSame(e, only().throwable());
    }

    @Test
    void warnCanCarryAnException() {
        var e = new IllegalStateException("recovered");
        this.log.warn(e, "Retrying %s", "load");

        assertEquals(Level.WARN, only().level());
        assertEquals("Retrying load", only().message());
        assertSame(e, only().throwable());
    }

    @Test
    void errorWithoutExceptionIsFine() {
        this.log.error(null, "no exception here");

        assertNull(only().throwable());
    }

    // ---- Debug and trace -------------------------------------------------------------------------------------

    @Test
    void debugIsOnlyLoggedWhenDebugging() {
        this.log.debug("hidden");
        assertTrue(this.recorder.entries.isEmpty());

        ModLog.setDebug(true);
        this.log.debug("shown %d", 1);

        assertEquals(ModLog.DEBUG_PREFIX + "shown 1", only().message());
        assertEquals(Level.INFO, only().level(), "debug output is at INFO so it shows with default settings");
    }

    @Test
    void debugSupplierIsNotCalledWhenNotDebugging() {
        var calls = new AtomicInteger();

        this.log.debug(() -> "built " + calls.incrementAndGet());

        assertEquals(0, calls.get());
        assertTrue(this.recorder.entries.isEmpty());
    }

    @Test
    void traceNeedsDebuggingAndTheBit() {
        ModLog.setTraceMask(0x4);
        this.log.debug(0x4, "not debugging");
        assertTrue(this.recorder.entries.isEmpty());

        ModLog.setDebug(true);
        this.log.debug(0x2, "other bit");
        assertTrue(this.recorder.entries.isEmpty());

        this.log.debug(0x6, "matching bit");
        assertEquals(ModLog.DEBUG_PREFIX + "matching bit", only().message());
    }

    @Test
    void traceSupplierIsNotCalledWhenItsBitIsOff() {
        // Regression: with debugging on, the supplier ran even when the trace bit was off
        ModLog.setDebug(true);
        ModLog.setTraceMask(0x1);
        var calls = new AtomicInteger();

        this.log.debug(0x2, () -> "built " + calls.incrementAndGet());
        assertEquals(0, calls.get());
        assertTrue(this.recorder.entries.isEmpty());

        this.log.debug(0x1, () -> "built " + calls.incrementAndGet());
        assertEquals(1, calls.get());
        assertEquals(ModLog.DEBUG_PREFIX + "built 1", only().message());
    }

    @Test
    void settingsAreGlobal() {
        var other = new ModLog(new RecordingLogger("other"));

        ModLog.setDebug(true);
        ModLog.setTraceMask(0x8);

        assertTrue(this.log.isDebugging());
        assertTrue(other.isDebugging());
        assertTrue(other.isTracing(0x8));
        assertFalse(other.isTracing(0x1));
    }

    @Test
    void multiLineDebugIsPrefixedOnEveryLine() {
        ModLog.setDebug(true);
        this.log.debug("one\ntwo");

        assertEquals(List.of(ModLog.DEBUG_PREFIX + "one", ModLog.DEBUG_PREFIX + "two"), this.messages());
    }

    // ---- Suppliers -------------------------------------------------------------------------------------------

    @Test
    void supplierMessagesAreNotFormatted() {
        this.log.info(() -> "50% of %s");

        assertEquals("50% of %s", only().message());
    }

    // ---- Children --------------------------------------------------------------------------------------------

    @Test
    void childIsNamedAfterItsParent() {
        var child = ModLog.createChild(this.log, "Scanner");

        var modLog = assertInstanceOf(ModLog.class, child);
        assertEquals("dsurround/Scanner", modLog.getName());
    }

    @Test
    void childOfAnotherLoggerIsTheParentItself() {
        // Regression: this threw, so code creating a child logger couldn't be given a test double
        IModLog other = new IModLog() {
            @Override
            public boolean isDebugging() {
                return false;
            }

            @Override
            public boolean isTracing(int mask) {
                return false;
            }

            @Override
            public void log(Level level, Throwable t, String format, Object... params) {
            }
        };

        assertSame(other, ModLog.createChild(other, "Child"));
    }
}
