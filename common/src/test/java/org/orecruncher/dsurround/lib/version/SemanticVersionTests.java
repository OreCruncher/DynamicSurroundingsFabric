package org.orecruncher.dsurround.lib.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parsing and ordering semantic versions.
 */
public class SemanticVersionTests {

    private static SemanticVersion v(String text) {
        try {
            return SemanticVersion.parse(text);
        } catch (ParseException e) {
            throw new AssertionError("'" + text + "' should parse", e);
        }
    }

    // ---- Parsing ---------------------------------------------------------------------------------------------

    @Test
    void parsesAllParts() {
        var version = v("1.2.3-beta.11+build.5");

        assertEquals(1, version.major);
        assertEquals(2, version.minor);
        assertEquals(3, version.patch);
        assertArrayEquals(new String[]{"beta", "11"}, version.getPreRelease());
        assertArrayEquals(new String[]{"build", "5"}, version.getBuildMeta());
        assertEquals("1.2.3-beta.11+build.5", version.toString());
    }

    @Test
    void parsesBuildMetaWithoutPreRelease() {
        var version = v("0.4.6+fabric");

        assertEquals(0, version.getPreRelease().length);
        assertTrue(version.hasBuildMeta("fabric"));
    }

    @Test
    void invalidVersionsThrowParseException() {
        // Regression: "1" and "1.2" threw ArrayIndexOutOfBoundsException, and numbers too big for an int threw
        // NumberFormatException
        var invalid = List.of(
            "", "1", "1.2", "1.", "1.2.", "1..3", ".1.2",                   // missing parts
            "01.2.3", "1.02.3", "1.2.03",                                  // leading zeros
            "1.2.3-", "1.2.3+", "1.2.3-a..b", "1.2.3+a..b", "1.2.3-a+",    // empty identifiers
            "1.2.3x", "1.2.3-a_b", "v1.2.3", " 1.2.3",                     // junk
            "99999999999.0.0", "1.99999999999.0", "1.0.99999999999"        // too big for an int
        );
        assertAll(invalid.stream().map(text -> (Executable) () ->
                assertThrows(ParseException.class, () -> SemanticVersion.parse(text), "'" + text + "'")));
    }

    @Test
    void errorOffsetIsWhereTheProblemIs() {
        var e = assertThrows(ParseException.class, () -> SemanticVersion.parse("1.2"));
        assertEquals(3, e.getErrorOffset());

        e = assertThrows(ParseException.class, () -> SemanticVersion.parse("1.2.3x"));
        assertEquals(5, e.getErrorOffset());
    }

    // ---- Minecraft versions ----------------------------------------------------------------------------------

    @Test
    void minecraftVersionWithoutAPatchGetsZero() throws ParseException {
        // Regression: Minecraft 1.21 couldn't be parsed, so the update check never ran on it
        assertEquals(v("1.21.0"), SemanticVersion.parseMinecraft("1.21"));
        assertEquals(v("1.21.0-rc.1"), SemanticVersion.parseMinecraft("1.21-rc.1"));
        assertEquals(v("1.21.0+build.3"), SemanticVersion.parseMinecraft("1.21+build.3"));
    }

    @Test
    void minecraftVersionWithAPatchIsUnchanged() throws ParseException {
        assertEquals(v("1.21.1"), SemanticVersion.parseMinecraft("1.21.1"));
        assertEquals(v("1.20.4-pre.2"), SemanticVersion.parseMinecraft("1.20.4-pre.2"));
    }

    @Test
    void snapshotIsNotAVersion() {
        assertThrows(ParseException.class, () -> SemanticVersion.parseMinecraft("24w14a"));
    }

    // ---- Ordering --------------------------------------------------------------------------------------------

    @Test
    void ordersAsTheSpecificationDoes() {
        // The example from semver.org, section 11
        var expected = List.of(
                v("1.0.0-alpha"), v("1.0.0-alpha.1"), v("1.0.0-alpha.beta"), v("1.0.0-beta"),
                v("1.0.0-beta.2"), v("1.0.0-beta.11"), v("1.0.0-rc.1"), v("1.0.0"),
                v("1.0.1"), v("1.1.0"), v("2.0.0"));
        var shuffled = new ArrayList<>(expected);
        Collections.reverse(shuffled);
        Collections.sort(shuffled);

        assertEquals(expected, shuffled);
    }

    @Test
    void numbersCompareAsNumbers() {
        assertTrue(v("0.10.0").compareTo(v("0.9.0")) > 0, "not compared as text");
        assertTrue(v("1.0.0-beta.11").compareTo(v("1.0.0-beta.2")) > 0);
    }

    @Test
    void buildMetaIsIgnoredForOrderingButNotEquality() {
        assertEquals(0, v("1.0.0+a").compareTo(v("1.0.0+b")));
        assertNotEquals(v("1.0.0+a"), v("1.0.0+b"));
        assertEquals(v("1.0.0+a"), v("1.0.0+a"));
        assertEquals(v("1.0.0+a").hashCode(), v("1.0.0+a").hashCode());
    }

    @Test
    void updateChecks() {
        assertTrue(v("0.4.7").isUpdateFor(v("0.4.6")));
        assertFalse(v("0.4.6").isUpdateFor(v("0.4.6")));
        assertTrue(v("0.4.6").isUpdateFor(v("0.4.6-beta.1")), "a release is newer than its pre-releases");
    }
}
