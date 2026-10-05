package org.orecruncher.dsurround.lib.scripting;

/**
 * Core math functions that are automatically defined for the Script Engine when it is initialized
 */
public final class MathFunctions {

    public static void configure(IConfigureDefinition setup) {

        setup.defineVariable("math.pi", ConstantVariable.of(Math.PI));
        setup.defineVariable("math.tau", ConstantVariable.of(Math.TAU));
        setup.defineVariable("math.e", ConstantVariable.of(Math.E));

        setup.numberFunction("math.cos", Math::cos);
        setup.numberFunction("math.sin", Math::sin);
        setup.numberFunction("math.tan", Math::tan);
        setup.numberFunction("math.toDegrees", Math::toDegrees);
        setup.numberFunction("math.toRadians", Math::toRadians);

        setup.numberFunction("math.pow", Math::pow);
        setup.numberFunction("math.log", Math::log);
        setup.numberFunction("math.exp", Math::exp);

        setup.numberFunction("math.sqrt", Math::sqrt);
        setup.numberFunction("math.abs", Math::abs);

        // math.round(value) or math.round(value, places)
        setup.function("math.round")
                .param(ArgType.NUMBER)
                .optional(ArgType.INTEGER)
                .pure()
                .handler(args -> round(args.number(0), args.count() > 1 ? args.integer(1) : 0));

        // Not pure: a different result on every call
        setup.function("math.random")
                .handler(args -> Math.random());
    }

    /**
     * Rounds to the number of decimal places. A negative number of places rounds to the left of the decimal
     * point, so round(1234, -2) is 1200.
     */
    private static double round(double number, int places) {
        if (places == 0)
            return Math.round(number);
        if (places > 0) {
            var factor = Math.pow(10, places);
            return Math.round(number * factor) / factor;
        }
        var factor = Math.pow(10, -places);
        return Math.round(number / factor) * factor;
    }
}
