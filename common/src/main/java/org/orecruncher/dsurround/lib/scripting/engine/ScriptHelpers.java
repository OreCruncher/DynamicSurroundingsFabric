package org.orecruncher.dsurround.lib.scripting.engine;

public class ScriptHelpers {

    public static boolean toBoolean(Object value) {
        try {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value instanceof String s) {
                return "true".equalsIgnoreCase(s);
            }
            if (value instanceof Number n) {
                return n.doubleValue() != 0;
            }
            if (value == null)
                return false;
        } catch (Throwable ignored) {}
        ScriptException.throwException("Value provided is not a boolean");
        return false;
    }

    public static double toDouble(Object value) {
        try {
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            if (value instanceof String s) {
                return Double.parseDouble(s);
            }
        } catch(Throwable ignored){}
        ScriptException.throwException("Value provided is not a number");
        return 0;
    }

    public static int toInteger(Object value) {
        try {
            if (value instanceof Integer i) {
                return i;
            }

            if (value instanceof String s) {
                return Integer.parseInt(s);
            }
        } catch(Throwable ignored) {}
        ScriptException.throwException("Value provided is not an integer");
        return 0;
    }
}
