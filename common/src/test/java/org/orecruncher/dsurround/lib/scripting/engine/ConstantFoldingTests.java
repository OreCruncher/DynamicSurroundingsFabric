package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.collections.Pair;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;

public class ConstantFoldingTests {

    private static final List<Pair<String, Boolean>> LOGICAL_TEST_CASES = ImmutableList.of(
            Pair.of("true", true),
            Pair.of("false", false),
            Pair.of("!(!true || !(!false && true))", true),
            Pair.of("!!!!true", true),
            Pair.of("!(true || false)", false),
            Pair.of("!true && !false", false),
            // String conversion to boolean
            Pair.of("!'true'", false),
            Pair.of("!'false'", true),
            Pair.of("'true' || 'false'", true),
            Pair.of("'true' && 'false'", false)
    );

    private static final List<String> NUMERIC_ZERO_TEST_CASES = ImmutableList.of(
            "(5 * 5) * 0",
            "6 * (5 * 0)",
            "6 * (5 * 0) + (9 * 9) * 0",
            "0 + 0"
    );

    private static final List<Pair<String, String>> STRING_TESTS = ImmutableList.of(
            Pair.of("'test'", "test"),
            Pair.of("\"test\"", "test"),
            Pair.of("'first' + 'second'", "firstsecond"),
            Pair.of("'this' + 'is' + 'sparta'", "thisissparta")
    );

    @TestFactory
    public Stream<DynamicTest> logicDynamicTests() {
        var scriptEngine = new ScriptEngine();
        return LOGICAL_TEST_CASES.stream()
                .map(data -> DynamicTest.dynamicTest("Folding \"%s\"".formatted(data.first()), () -> {
                    var expression = scriptEngine.compile(data.first());
                    assertNotNull(expression);
                    assertInstanceOf(Expression.Literal.class, expression);
                    assertInstanceOf(Boolean.class, expression.eval());
                    assertEquals(data.second(), expression.eval());
                }));
    }

    @TestFactory
    public Stream<DynamicTest> numericZeroDynamicTests() {
        var scriptEngine = new ScriptEngine();
        return NUMERIC_ZERO_TEST_CASES.stream()
                .map(data -> DynamicTest.dynamicTest("Folding \"%s\"".formatted(data), () -> {
                    var expression = scriptEngine.compile(data);
                    assertNotNull(expression);
                    assertInstanceOf(Expression.Literal.class, expression);
                    var result = expression.eval();
                    assertInstanceOf(Double.class, result);
                    assertEquals(0.0D, result);
                }));
    }

    @TestFactory
    public Stream<DynamicTest> stringDynamicTests() {
        var scriptEngine = new ScriptEngine();
        return STRING_TESTS.stream()
                .map(data -> DynamicTest.dynamicTest("Folding \"%s\"".formatted(data.first()), () -> {
                    var expression = scriptEngine.compile(data.first());
                    assertNotNull(expression);
                    assertInstanceOf(Expression.Literal.class, expression);
                    assertInstanceOf(String.class, expression.eval());
                    assertEquals(data.second(), expression.eval());
                }));
    }
}
