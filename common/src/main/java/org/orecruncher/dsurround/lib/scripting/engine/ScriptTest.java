package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class ScriptTest {

    private static final List<String> scripts = ImmutableList.of(
            "math.tau == 2*math.pi",
            //"!!!test1",
            //"!!!!test1",
            //"(diurnal.getCelestialAngle()*360)",
            //"!(0 * 2)",
            //"1 !+ 45",
            //"1 + !45",
            "!(!true || !(!false && true))",
            "!!!!true",
            "!(true || false)",
            "!true && !false"
    );

    public static void runScriptTests() {
        var scriptEngine = new ScriptEngine();
        scriptEngine.defineFunction("diurnal.getCelestialAngle", 0, l -> 0.25);
        scriptEngine.defineVariable("test1", () -> true);
        for (String script : scripts) {
            try {
                var result = scriptEngine.compile(script);
                var data = result.eval();
                var x = 0;
            } catch(Throwable t) {
                var x = 0;
            }
        }
    }
}
