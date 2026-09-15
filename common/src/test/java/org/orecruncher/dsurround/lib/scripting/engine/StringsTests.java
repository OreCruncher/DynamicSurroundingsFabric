package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.collections.Pair;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class StringsTests {

    private static final List<Pair<String, String>> STRING_TESTS = ImmutableList.of(
            Pair.of("'test'", "test"),
            Pair.of("\"test\"", "test"),
            Pair.of("'first' + 'second'", "firstsecond"),
            Pair.of("'this' + 'is' + 'sparta'", "thisissparta"),
            Pair.of("'zero' + 0", "zero0.0")
    );

    @TestFactory
    public Stream<DynamicTest> stringDynamicTests() {
        var scriptEngine = new ScriptEngine();
        return STRING_TESTS.stream()
                .map(data -> DynamicTest.dynamicTest("Evaluating \"%s\"".formatted(data.first()), () -> {
                    var expression = scriptEngine.compile(data.first());
                    assertNotNull(expression);
                    assertInstanceOf(Expression.Literal.class, expression);
                    assertInstanceOf(String.class, expression.eval());
                    assertEquals(data.second(), expression.eval());
                }));
    }
}
