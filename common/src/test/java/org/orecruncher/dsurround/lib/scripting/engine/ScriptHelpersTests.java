package org.orecruncher.dsurround.lib.scripting.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Unit tests for the value conversions in {@link ScriptHelpers}, in particular the non-throwing variants used on
 * the evaluation hot path, and for {@link ScriptException} not capturing stack traces.
 */
@DisplayName("ScriptHelpers")
public class ScriptHelpersTests {

    /**
     * Strings covering every branch of the decimal validator plus forms it must reject or defer to the JDK.
     */
    private static final String[] NUMERIC_STRINGS = {
            // plain and signed numbers
            "0", "42", "-42", "+42", "007", "3.14", "-3.14", "+.5", "-.5", ".5", "5.", "5.0",
            // exponents
            "1e3", "1E3", "1e+3", "1e-3", "-1.5E-10", ".5e2", "5.e2",
            // suffixes
            "1d", "1D", "1f", "1F", "1.5e10F", "2.d",
            // special values
            "NaN", "-NaN", "+NaN", "Infinity", "-Infinity", "+Infinity",
            // surrounding whitespace, including control characters trimmed like String.trim()
            " 12 ", "\t3\n", "\u000112\u0002", "  -1.5e3  ",
            // hexadecimal floating point (deferred to the JDK)
            "0x1p3", "0X1P-2", "-0x1.8p1", "0x1", "0xg",
            // rejects
            "", " ", ".", "+", "-", "e5", "1e", "1e+", "1.2.3", "1_000", "1,000", "abc", "1a", "a1",
            "nan", "infinity", "NaNx", "Infinityx", "1 2", "--1", "+-1", "1dd", "1d1", "1.5ef",
            // non-ASCII digits are not accepted by Double.parseDouble
            "٣", "1٣",
            // other whitespace is not trimmed
            " 12", "12 "
    };

    private static Double jdkParse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String printable(String s) {
        var sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < ' ' || c > '~')
                sb.append("\\u%04x".formatted((int) c));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    @TestFactory
    @DisplayName("parseDoubleOrNull accepts exactly what Double.parseDouble accepts")
    Stream<DynamicTest> parseMatchesJdk() {
        return Arrays.stream(NUMERIC_STRINGS).map(s -> {
            var expected = jdkParse(s);
            return dynamicTest("[%s]  =>  %s".formatted(printable(s), expected),
                    () -> assertEquals(expected, ScriptHelpers.parseDoubleOrNull(s)));
        });
    }

    @TestFactory
    @DisplayName("tryToDouble")
    Stream<DynamicTest> tryToDouble() {
        return Stream.of(
                dynamicTest("Double", () -> assertEquals(2.5, ScriptHelpers.tryToDouble(2.5))),
                dynamicTest("Integer", () -> assertEquals(3.0, ScriptHelpers.tryToDouble(3))),
                dynamicTest("Long", () -> assertEquals(4.0, ScriptHelpers.tryToDouble(4L))),
                dynamicTest("Float", () -> assertEquals(1.5, ScriptHelpers.tryToDouble(1.5F))),
                dynamicTest("numeric string", () -> assertEquals(-7.0, ScriptHelpers.tryToDouble("-7"))),
                dynamicTest("non-numeric string", () -> assertNull(ScriptHelpers.tryToDouble("abc"))),
                dynamicTest("boolean", () -> assertNull(ScriptHelpers.tryToDouble(true))),
                dynamicTest("null", () -> assertNull(ScriptHelpers.tryToDouble(null))),
                dynamicTest("other object", () -> assertNull(ScriptHelpers.tryToDouble(new Object())))
        );
    }

    @TestFactory
    @DisplayName("tryToBoolean")
    Stream<DynamicTest> tryToBoolean() {
        return Stream.of(
                dynamicTest("true", () -> assertEquals(Boolean.TRUE, ScriptHelpers.tryToBoolean(true))),
                dynamicTest("false", () -> assertEquals(Boolean.FALSE, ScriptHelpers.tryToBoolean(false))),
                dynamicTest("'TRUE' (case-insensitive)", () -> assertEquals(Boolean.TRUE, ScriptHelpers.tryToBoolean("TRUE"))),
                dynamicTest("any other string is false", () -> assertEquals(Boolean.FALSE, ScriptHelpers.tryToBoolean("yes"))),
                dynamicTest("non-zero number", () -> assertEquals(Boolean.TRUE, ScriptHelpers.tryToBoolean(0.5))),
                dynamicTest("zero", () -> assertEquals(Boolean.FALSE, ScriptHelpers.tryToBoolean(0))),
                dynamicTest("null is false", () -> assertEquals(Boolean.FALSE, ScriptHelpers.tryToBoolean(null))),
                dynamicTest("other object", () -> assertNull(ScriptHelpers.tryToBoolean(new Object())))
        );
    }

    @TestFactory
    @DisplayName("Throwing conversions report a single ScriptException")
    Stream<DynamicTest> throwingConversions() {
        return Stream.of(
                dynamicTest("toDouble of a non-numeric string", () -> {
                    var ex = assertThrows(ScriptException.class, () -> ScriptHelpers.toDouble("abc"));
                    assertEquals("Value provided is not a number", ex.getReason());
                    assertNull(ex.getCause());
                }),
                dynamicTest("toDouble of an object", () ->
                        assertThrows(ScriptException.class, () -> ScriptHelpers.toDouble(new Object()))),
                dynamicTest("toBoolean of an object", () -> {
                    var ex = assertThrows(ScriptException.class, () -> ScriptHelpers.toBoolean(new Object()));
                    assertEquals("Value provided is not a boolean", ex.getReason());
                }),
                dynamicTest("toDouble of a numeric string", () -> assertEquals(1.5, ScriptHelpers.toDouble(" 1.5 "))),
                dynamicTest("toBoolean of null", () -> assertFalse(ScriptHelpers.toBoolean(null)))
        );
    }

    @Test
    @DisplayName("ScriptException does not capture a stack trace")
    void stackless() {
        var ex = assertThrows(ScriptException.class, () -> new ScriptEngine().compile("1 +"));
        assertEquals(0, ex.getStackTrace().length);
        assertEquals("(1, 3) Expected 2 operands, but found 1", ex.getMessage());
    }
}
