package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.collections.Pair;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class FunctionTests {

    private static final List<Pair<String, Object>> TEST_DATA = ImmutableList.of(
            Pair.of("lib.oneof(Math.PI, 0, Math.TAU, Math.PI)", Boolean.TRUE),
            Pair.of("!lib.oneof(Math.PI, 0, Math.TAU, Math.PI)", Boolean.FALSE),
            Pair.of("Math.PI * 2 == Math.TAU", Boolean.TRUE),
            Pair.of("math.abs(-Math.PI)", Math.PI),
            Pair.of("lib.iif(Math.TAU != Math.PI * 2, 'its twue! its twue!', 'naah') == 'naah'", Boolean.TRUE),
            Pair.of("lib.isbetween( 7, 8, 10)", Boolean.FALSE),
            Pair.of("lib.isbetween( 7, 6, 10)", Boolean.TRUE),
            Pair.of("math.cos(math.toRadians(45))", Math.sqrt(2D) / 2.0D),
            Pair.of("math.cos(math.toRadians(45)) == math.sqrt(2)/2", Boolean.TRUE)
    );

    // From the runtime diagnostic overlay
    private static final List<Pair<String, String>> DIAGNOSTIC_TEST_DATA = ImmutableList.of(
            Pair.of(
                    "'Dim: ' + dim.getId() + '/' + dim.getDimName() + '; isSuperFlat: ' + dim.isSuperFlat()",
                    "Dim: test:aroni/The Test of Aroni; isSuperFlat: true"),
            Pair.of(
                    "'Biome: ' + biome.getName() + ' (' + biome.getId() + '); Temp ' + biome.getTemperature() + '; rainfall: ' + biome.getRainfall()",
                    "Biome: Vaudeville (test:vaudeville); Temp 0.8; rainfall: 0.33"),
            Pair.of(
                    "'Biome Traits: ' + biome.getTraits()",
                    "Biome Traits: [REALLY,COLD,TODAY]"),
            Pair.of(
                    "'Weather: ' + lib.iif(weather.isRaining(),'rain: ' + weather.getRainIntensity(),'not raining') + lib.iif(weather.isThundering(),' thundering','') + '; Temp: ' + weather.getTemperature() + '; ice: ' + lib.iif(weather.getTemperature() < 0.15, 'true', 'false') + ' ' + lib.iif(weather.getTemperature() < 0.2, '(breath)', '')",
                    "Weather: rain: 0.9 thundering; Temp: 0.1; ice: true (breath)"),
            Pair.of(
                    "'Diurnal: ' + lib.iif(diurnal.isNight(),' night',' day') + '; celestial angle: ' + diurnal.getCelestialAngle() + '; degrees: ' + (diurnal.getCelestialAngle()*360)",
                    "Diurnal:  day; celestial angle: 45; degrees: 16200.0"),
            Pair.of(
                    "'Player: health ' + player.getHealth() + '/' + player.getMaxHealth() + '; food ' + player.getFoodLevel() + '/' + player.getFoodSaturationLevel() + '; pos (' + player.getX() + ', ' + player.getY() + ', ' + player.getZ() + ')'",
                    "Player: health 15/20; food 20/20; pos (100, 64, -100)"),
            Pair.of(
                    "'State: isInside ' + state.isInside() + '; inVillage ' + state.isInVillage() + '; isUnderWater ' + state.isUnderWater()",
                    "State: isInside false; inVillage true; isUnderWater false")
    );

    @TestFactory
    public Stream<DynamicTest> functionDynamicTests() {
        var scriptEngine = new ScriptEngine();
        return TEST_DATA.stream()
                .map(data -> DynamicTest.dynamicTest("Evaluating \"%s\"".formatted(data.first()), () -> {
                    var expression = scriptEngine.compile(data.first());
                    assertNotNull(expression);
                    assertEquals(data.second(), expression.eval());
                }));
    }

    @TestFactory
    public Stream<DynamicTest> diagnosticDynamicTests() {
        var scriptEngine = new ScriptEngine();
        scriptEngine.defineFunction("dim.getId", a -> "test:aroni");
        scriptEngine.defineFunction("dim.getDimName", a -> "The Test of Aroni");
        scriptEngine.defineFunction("dim.isSuperFlat", a -> true);
        scriptEngine.defineFunction("biome.getName", a -> "Vaudeville");
        scriptEngine.defineFunction("biome.getId", a -> "test:vaudeville");
        scriptEngine.defineFunction("biome.getTemperature", a -> 0.8D);
        scriptEngine.defineFunction("biome.getRainfall", a -> 0.33D);
        scriptEngine.defineFunction("biome.getTraits", a -> "[REALLY,COLD,TODAY]");
        scriptEngine.defineFunction("weather.isRaining", a -> true);
        scriptEngine.defineFunction("weather.getRainIntensity", a -> 0.9D);
        scriptEngine.defineFunction("weather.isThundering", a -> true);
        scriptEngine.defineFunction("weather.getTemperature", a -> 0.1D);
        scriptEngine.defineFunction("diurnal.isNight", a -> false);
        scriptEngine.defineFunction("diurnal.getCelestialAngle", a -> 45);
        scriptEngine.defineFunction("player.getHealth", a -> 15);
        scriptEngine.defineFunction("player.getMaxHealth", a -> 20);
        scriptEngine.defineFunction("player.getFoodLevel", a -> 20);
        scriptEngine.defineFunction("player.getFoodSaturationLevel", a -> 20);
        scriptEngine.defineFunction("player.getX", a -> 100);
        scriptEngine.defineFunction("player.getY", a -> 64);
        scriptEngine.defineFunction("player.getZ", a -> -100);
        scriptEngine.defineFunction("state.isInside", a -> false);
        scriptEngine.defineFunction("state.isInVillage", a -> true);
        scriptEngine.defineFunction("state.isUnderWater", a -> false);
        return DIAGNOSTIC_TEST_DATA.stream()
                .map(data -> DynamicTest.dynamicTest("Diagnostic \"%s\"".formatted(data.first()), () -> {
                    var expression = scriptEngine.compile(data.first());
                    assertNotNull(expression);
                    var text = expression.eval();
                    assertEquals(data.second(), text);
                }));
    }
}
