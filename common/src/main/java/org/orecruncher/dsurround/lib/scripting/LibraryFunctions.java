package org.orecruncher.dsurround.lib.scripting;

import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;

import java.time.*;
import java.util.regex.Pattern;

/**
 * Core functions that are automatically defined for the Script Engine when it is initialized
 */
public final class LibraryFunctions {

    public static void configure(IConfigureDefinition config) {
        config.defineFunction("lib.iif", 3, LibraryFunctions::iif);
        config.defineFunction("lib.match", 2, LibraryFunctions::match);
        config.defineFunction("lib.oneOf", -2, LibraryFunctions::oneof);
        config.defineFunction("lib.isBetween", 3, LibraryFunctions::isBetween);
    }

    private static Object iif(final Object[] args) {
        var flag = ScriptHelpers.toBoolean(args[0]);
        return flag ? args[1] : args[2];
    }

    private static boolean match(final Object[] args) {
        return Pattern.matches(args[0].toString(), args[1].toString());
    }

    private static boolean oneof(final Object[] args) {
        var testee = args[0];
        for (int i = 1; i < args.length; i++)
            if (testee.equals(args[i]))
                return true;
        return false;
    }

    private static boolean isBetween(final Object[] args) {
        var value = ScriptHelpers.toDouble(args[0]);
        var min = ScriptHelpers.toDouble(args[1]);
        var max = ScriptHelpers.toDouble(args[2]);
        return value >= min && value <= max;
    }
}