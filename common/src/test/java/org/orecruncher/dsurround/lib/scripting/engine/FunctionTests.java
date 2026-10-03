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
            Pair.of("math.cos(math.toRadians(45)) == math.sqrt(2)/2", Boolean.TRUE),
            Pair.of("math.round(3.33333)", 3.0D),
            Pair.of("math.round(3.33333, 2)", 3.33D)
    );

    // From the runtime diagnostic overlay
    private static final List<Pair<String, String>> DIAGNOSTIC_TEST_DATA = ImmutableList.of(
            Pair.of(
                    "'Dim: ' + dim.getId() + '/' + dim.getDimName() + '; isSuperFlat: ' + dim.isSuperFlat()",
                    "Dim: test:aroni/The Test of Aroni; isSuperFlat: true"),
            Pair.of(
                    "'Biome: ' + biome.getName() + ' (' + biome.getId() + '); Temp ' + math.round(biome.getTemperature(), 2) + '; rainfall: ' + math.round(biome.getRainfall(), 2)",
                    "Biome: Vaudeville (test:vaudeville); Temp 0.8; rainfall: 0.33"),
            Pair.of(
                    "'Biome Traits: ' + biome.getTraits()",
                    "Biome Traits: [REALLY,COLD,TODAY]"),
            Pair.of(
                    "'Weather: ' + lib.iif(weather.isRaining(),'rain: ' + math.round(weather.getRainIntensity(), 2),'not raining') + lib.iif(weather.isThundering(),' thundering','') + '; Temp: ' + math.round(weather.getTemperature(), 2) + '; ice: ' + lib.iif(weather.getTemperature() < 0.15, 'true', 'false') + ' ' + lib.iif(weather.getTemperature() < 0.2, '(breath)', '')",
                    "Weather: rain: 0.9 thundering; Temp: 0.1; ice: true (breath)"),
            Pair.of(
                    "'Diurnal: ' + lib.iif(diurnal.isNight(),'night','day') + '; celestial angle: ' + math.round(diurnal.getCelestialAngle(), 2) + '; degrees: ' + math.round(diurnal.getCelestialAngle()*360, 2)",
                    "Diurnal: day; celestial angle: 45; degrees: 16200"),
            Pair.of(
                    "'Player: health ' + player.getHealth() + '/' + player.getMaxHealth() + '; food ' + player.getFoodLevel() + '/' + player.getFoodSaturationLevel() + '; pos (' + math.round(player.getX(), 2) + ', ' + math.round(player.getY(), 2) + ', ' + math.round(player.getZ(), 2) + ')'",
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
        scriptEngine.property("dim.getId", () -> "test:aroni");
        scriptEngine.property("dim.getDimName", () -> "The Test of Aroni");
        scriptEngine.property("dim.isSuperFlat", () -> true);
        scriptEngine.property("biome.getName", () -> "Vaudeville");
        scriptEngine.property("biome.getId", () -> "test:vaudeville");
        scriptEngine.property("biome.getTemperature", () -> 0.8D);
        scriptEngine.property("biome.getRainfall", () -> 0.33D);
        scriptEngine.property("biome.getTraits", () -> "[REALLY,COLD,TODAY]");
        scriptEngine.property("weather.isRaining", () -> true);
        scriptEngine.property("weather.getRainIntensity", () -> 0.9D);
        scriptEngine.property("weather.isThundering", () -> true);
        scriptEngine.property("weather.getTemperature", () -> 0.1D);
        scriptEngine.property("diurnal.isNight", () -> false);
        scriptEngine.property("diurnal.getCelestialAngle", () -> 45);
        scriptEngine.property("player.getHealth", () -> 15);
        scriptEngine.property("player.getMaxHealth", () -> 20);
        scriptEngine.property("player.getFoodLevel", () -> 20);
        scriptEngine.property("player.getFoodSaturationLevel", () -> 20);
        scriptEngine.property("player.getX", () -> 100);
        scriptEngine.property("player.getY", () -> 64);
        scriptEngine.property("player.getZ", () -> -100);
        scriptEngine.property("state.isInside", () -> false);
        scriptEngine.property("state.isInVillage", () -> true);
        scriptEngine.property("state.isUnderWater", () -> false);
        return DIAGNOSTIC_TEST_DATA.stream()
                .map(data -> DynamicTest.dynamicTest("Diagnostic \"%s\"".formatted(data.first()), () -> {
                    var expression = scriptEngine.compile(data.first());
                    assertNotNull(expression);
                    var text = expression.eval();
                    assertEquals(data.second(), text);
                }));
    }
}
