package org.orecruncher.dsurround.lib.scripting.engine;

public class ScriptHelpers {

    public static boolean toBoolean(Object value) {
        var result = tryToBoolean(value);
        if (result == null)
            ScriptException.throwException("Value provided is not a boolean");
        return result;
    }

    /**
     * Converts a value to a Boolean without throwing.
     *
     * @return The converted value, or null if the value cannot be converted.
     */
    public static Boolean tryToBoolean(Object value) {
        if (value instanceof Boolean b)
            return b;
        if (value instanceof String s)
            return "true".equalsIgnoreCase(s);
        if (value instanceof Number n)
            return n.doubleValue() != 0;
        if (value == null)
            return Boolean.FALSE;
        return null;
    }

    public static double toDouble(Object value) {
        if (value instanceof Number n)
            return n.doubleValue();
        var result = tryToDouble(value);
        if (result == null)
            ScriptException.throwException("Value provided is not a number");
        return result;
    }

    /**
     * Converts a value to a Double without throwing. Callers on a hot path should handle {@link Number} themselves
     * first to avoid boxing.
     *
     * @return The converted value, or null if the value cannot be converted.
     */
    public static Double tryToDouble(Object value) {
        if (value instanceof Number n)
            return n.doubleValue();
        if (value instanceof String s)
            return parseDoubleOrNull(s);
        return null;
    }

    /**
     * Parses a string the same way as {@link Double#parseDouble(String)}, but returns null instead of throwing
     * NumberFormatException. Decimal forms are validated up front so no exception is created; hexadecimal
     * floating point strings, which are rare, fall back to letting the JDK decide.
     */
    public static Double parseDoubleOrNull(String s) {
        if (isDecimalNumber(s))
            return Double.parseDouble(s);   // validated; cannot throw
        if (s.indexOf('x') >= 0 || s.indexOf('X') >= 0) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Matches the decimal forms accepted by {@link Double#parseDouble(String)}: optional surrounding whitespace,
     * optional sign, "NaN", "Infinity", or digits with an optional fraction and exponent, and an optional
     * f/F/d/D suffix.
     */
    private static boolean isDecimalNumber(String s) {
        int i = 0;
        int end = s.length();
        // Double.parseDouble trims characters <= ' ' like String.trim()
        while (i < end && s.charAt(i) <= ' ')
            i++;
        while (end > i && s.charAt(end - 1) <= ' ')
            end--;
        if (i == end)
            return false;

        char c = s.charAt(i);
        if (c == '+' || c == '-')
            i++;

        if (s.startsWith("NaN", i))
            return i + 3 == end;
        if (s.startsWith("Infinity", i))
            return i + 8 == end;

        int digits = 0;
        while (i < end && isAsciiDigit(s.charAt(i))) {
            i++;
            digits++;
        }
        if (i < end && s.charAt(i) == '.') {
            i++;
            while (i < end && isAsciiDigit(s.charAt(i))) {
                i++;
                digits++;
            }
        }
        if (digits == 0)
            return false;

        if (i < end && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            if (i < end && (s.charAt(i) == '+' || s.charAt(i) == '-'))
                i++;
            int exponentDigits = 0;
            while (i < end && isAsciiDigit(s.charAt(i))) {
                i++;
                exponentDigits++;
            }
            if (exponentDigits == 0)
                return false;
        }

        if (i < end && "fFdD".indexOf(s.charAt(i)) >= 0)
            i++;

        return i == end;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Converts the value to an integer. Any {@link Number} is accepted as long as it holds an integral value that
     * fits in an int (script number literals are always doubles, so {@code 2.0} is accepted but {@code 2.5} is not).
     */
    public static int toInteger(Object value) {
        var result = tryToInteger(value);
        if (result == null)
            ScriptException.throwException("Value provided is not an integer");
        return result;
    }

    /**
     * Converts a value to an Integer without throwing. Accepts integral numbers that fit in an int, and strings
     * of decimal digits with an optional sign (surrounding whitespace is ignored).
     *
     * @return The converted value, or null if the value cannot be converted.
     */
    public static Integer tryToInteger(Object value) {
        if (value instanceof Integer i)
            return i;
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE)
                return (int) d;
            return null;
        }
        if (value instanceof String s) {
            var text = s.trim();
            int start = !text.isEmpty() && (text.charAt(0) == '+' || text.charAt(0) == '-') ? 1 : 0;
            if (text.length() == start || text.length() - start > 10)
                return null;
            for (int i = start; i < text.length(); i++) {
                if (!isAsciiDigit(text.charAt(i)))
                    return null;
            }
            long parsed = Long.parseLong(text);   // validated above; cannot throw
            return parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE ? (int) parsed : null;
        }
        return null;
    }

    /**
     * Equality as seen by scripts. Numbers compare by numeric value regardless of their boxed type, so
     * {@code Integer 1 == Double 1.0}. Everything else uses {@link Object#equals(Object)}.
     */
    public static boolean isEqual(Object a, Object b) {
        if (a instanceof Number n1 && b instanceof Number n2) {
            return n1.doubleValue() == n2.doubleValue();
        }
        if (a == null || b == null)
            return a == b;
        return a.equals(b);
    }

    /**
     * Converts a value to a string for concatenation. Integral floating point values are rendered without a
     * trailing ".0" so that {@code 'Level ' + 3} produces "Level 3".
     */
    public static String toStringValue(Object value) {
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return Long.toString((long) d);
            }
        }
        return String.valueOf(value);
    }
}
