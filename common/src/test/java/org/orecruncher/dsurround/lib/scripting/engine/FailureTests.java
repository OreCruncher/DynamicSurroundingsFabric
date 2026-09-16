package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.collections.Pair;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class FailureTests {

    public static final List<Pair<String, Function<Exception, Boolean>>> TEST_CASES = ImmutableList.of(
            Pair.of("1..0", ex -> ex.getMessage().equals("(1, 3) Unexpected character '.'")),
            Pair.of("1 +", ex -> ex.getMessage().equals("(1, 3) Expected 2 operands, but found 1")),
            Pair.of("math.cos(Math.toRadians(45)", ex -> ex.getMessage().equals("(1, 9) Mismatched parentheses")),
            Pair.of("math.DoesNotExist(12 * Math.PI)", ex -> ex.getMessage().equals("(1, 18) Unexpected '(' (undefined function/typo?)")),
            Pair.of("(true || false) ||", ex -> ex.getMessage().equals("(1, 17) Expected 2 operands, but found 1")),
            Pair.of("1 + !45", ex -> ex.getMessage().equals("(1, 3) Incompatible operands for operator '+'")),
            Pair.of("4 % 2", ex -> ex.getMessage().equals("(1, 4) Unexpected character '%'")),
            Pair.of("()", ex -> ex.getMessage().equals("(-1, 0) Logic not detected in script")),
            Pair.of("", ex -> ex.getMessage().equals("(-1, 0) Empty script")),
            Pair.of("bad.parmVarArgs(1)", ex -> ex.getMessage().equals("(1, 18) Mismatched variable arguments: expected at least 2 but received 1")),
            Pair.of("bad.parm(1)", ex -> ex.getMessage().equals("(1, 11) Mismatched variable arguments: expected 2 but received 1")),
            Pair.of("bad.parm(1, 2, 3)", ex -> ex.getMessage().equals("(1, 17) Mismatched variable arguments: expected 2 but received 3"))
    );

    @TestFactory
    public Stream<DynamicTest> stringDynamicTests() {
        var scriptEngine = new ScriptEngine();
        scriptEngine.defineFunction("bad.parmVarArgs", 2, true, l -> l.length);
        scriptEngine.defineFunction("bad.parm", 2, false, l -> l.length);
        return TEST_CASES.stream()
                .map(data -> DynamicTest.dynamicTest("Failure testing \"%s\"".formatted(data.first()), () -> {
                    var exception = assertThrows(ScriptException.class,  () -> scriptEngine.compile(data.first()).eval());
                    assertNotNull(exception);
                    assertTrue(data.second().apply(exception));
                }));
    }
}
