package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.collections.Pair;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class NumbersTests {

    private static final List<Pair<String, Object>> TEST_DATA = ImmutableList.of(
            Pair.of("1", 1.0D),
            Pair.of("1.0", 1.0D),
            Pair.of("-1", -1.0D),
            Pair.of("-1.0", -1.0D),
            Pair.of("1 + -1", 0D),
            Pair.of("-Math.PI", -Math.PI),
            Pair.of("Math.PI * 2 - Math.TAU", 0D),
            Pair.of("Math.PI * 0", 0D)
    );

    @TestFactory
    public Stream<DynamicTest> numbersDynamicTests() {
        var scriptEngine = new ScriptEngine();
        return TEST_DATA.stream()
                .map(data -> DynamicTest.dynamicTest("Evaluating \"%s\"".formatted(data.first()), () -> {
                    var expression = scriptEngine.compile(data.first());
                    assertNotNull(expression);
                    assertEquals(data.second(), expression.eval());
                }));
    }
}
