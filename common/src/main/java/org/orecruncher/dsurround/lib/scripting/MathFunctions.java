package org.orecruncher.dsurround.lib.scripting;

import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;

/**
 * Core math functions that are automatically defined for the Script Engine when it is initialized
 */
public final class MathFunctions {

    public static void configure(IConfigureDefinition setup) {

        setup.defineVariable("math.pi", ConstantVariable.of(Math.PI));
        setup.defineVariable("math.tau", ConstantVariable.of(Math.TAU));
        setup.defineVariable("math.e", ConstantVariable.of(Math.E));

        setup.defineFunction("math.cos", 1, false, l -> Math.cos(toDouble(l[0])));
        setup.defineFunction("math.sin", 1, false, l -> Math.sin(toDouble(l[0])));
        setup.defineFunction("math.tan", 1, false, l -> Math.tan(toDouble(l[0])));
        setup.defineFunction("math.toDegrees", 1, false, l -> Math.toDegrees(toDouble(l[0])));
        setup.defineFunction("math.toRadians", 1, false, l -> Math.toRadians(toDouble(l[0])));

        setup.defineFunction("math.pow", 2, false, l -> Math.pow(toDouble(l[0]), toDouble(l[1])));
        setup.defineFunction("math.log", 1, false, l -> Math.log(toDouble(l[0])));
        setup.defineFunction("math.exp", 1, false, l -> Math.exp(toDouble(l[0])));

        setup.defineFunction("math.sqrt", 1, false, l -> Math.sqrt(toDouble(l[0])));
        setup.defineFunction("math.round", 1, true, MathFunctions::round);
        setup.defineFunction("math.abs", 1, false, l -> Math.abs(toDouble(l[0])));

        setup.defineFunction("math.random", l -> Math.random());
    }

    private static double toDouble(Object l) {
        return ScriptHelpers.toDouble(l);
    }

    private static double round(Object[] args) {
        double number = toDouble(args[0]);
        int places = 0;
        if (args.length == 2) {
            places = (int)toDouble(args[1]);
        }
        if (places < 1)
            return Math.round(number);
        var factor = Math.pow(10, places);
        return Math.round(number * factor) / factor;
    }
}
