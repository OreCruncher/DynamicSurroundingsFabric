package org.orecruncher.dsurround.lib.scripting.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Conditional;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Literal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * The conditional operator (condition ? whenTrue : whenFalse): grouping, laziness, conversion of the condition,
 * and compile-time selection. Error messages and locations are covered by {@link FailureTests}.
 */
@DisplayName("Conditional operator")
public class ConditionalOperatorTests {

    private ScriptEngine engine;
    private final AtomicInteger bombCalls = new AtomicInteger();
    private final AtomicInteger conditionCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        this.bombCalls.set(0);
        this.conditionCalls.set(0);

        var e = new ScriptEngine();
        e.defineVariable("t.yes", () -> true);
        e.defineVariable("t.no", () -> false);
        e.defineVariable("t.one", () -> 1.0);
        e.defineVariable("t.zero", () -> 0.0);
        e.defineVariable("t.text", () -> "abc");
        e.defineVariable("t.trueText", () -> "TRUE");
        e.defineVariable("t.nul", () -> null);
        e.defineVariable("t.counted", () -> {
            this.conditionCalls.incrementAndGet();
            return true;
        });
        e.function("t.bomb").handler(a -> {
            this.bombCalls.incrementAndGet();
            throw new RuntimeException("boom");
        });
        this.engine = e;
    }

    private Object eval(String script) {
        return this.engine.compile(script).eval();
    }

    private DynamicTest evaluates(String script, Object expected) {
        return dynamicTest(script, () -> assertEquals(expected, this.eval(script)));
    }

    @TestFactory
    @DisplayName("Selects a branch")
    Stream<DynamicTest> selects() {
        return Stream.of(
                evaluates("t.yes ? 1 : 2", 1.0),
                evaluates("t.no ? 1 : 2", 2.0),
                evaluates("t.yes ? 'a' : 2", "a"),
                evaluates("t.no ? 'a' : 2", 2.0),
                evaluates("t.one > 0 ? 'positive' : 'not'", "positive"),
                evaluates("t.yes?1:2", 1.0),
                evaluates("t.yes ?\n  1 :\n  2", 1.0),
                // negation directly after '?' and ':'
                evaluates("t.yes ? -1 : -2", -1.0),
                evaluates("t.no ? -1 : -2", -2.0),
                evaluates("t.no ? 1 : !t.yes", false)
        );
    }

    @TestFactory
    @DisplayName("The condition converts like any other boolean")
    Stream<DynamicTest> conversion() {
        return Stream.of(
                evaluates("t.one ? 'y' : 'n'", "y"),
                evaluates("t.zero ? 'y' : 'n'", "n"),
                evaluates("t.trueText ? 'y' : 'n'", "y"),
                evaluates("t.text ? 'y' : 'n'", "n"),
                evaluates("t.nul ? 'y' : 'n'", "n")
        );
    }

    @TestFactory
    @DisplayName("Binds more loosely than every other operator")
    Stream<DynamicTest> precedence() {
        return Stream.of(
                // the whole of a || b is the condition
                evaluates("t.no || t.yes ? 1 : 2", 1.0),
                evaluates("t.yes && t.no ? 1 : 2", 2.0),
                evaluates("t.one == 1 ? 1 : 2", 1.0),
                // the branches take whole expressions
                evaluates("t.yes ? 1 + 2 : 3 + 4", 3.0),
                evaluates("t.no ? 1 + 2 : 3 + 4", 7.0),
                evaluates("t.no ? 1 : t.no || t.yes", true),
                // inside a concatenation it needs parentheses ...
                evaluates("'is ' + (t.yes ? 'on' : 'off')", "is on"),
                // ... without them, the concatenation is the condition
                evaluates("'is ' + t.yes ? 'on' : 'off'", "off"),
                evaluates("(t.yes ? 2 : 3) * 4", 8.0),
                evaluates("!(t.yes ? t.no : t.yes)", true)
        );
    }

    @TestFactory
    @DisplayName("Chains and nests")
    Stream<DynamicTest> nesting() {
        return Stream.of(
                // right associative: a ? b : (c ? d : e)
                evaluates("t.no ? 1 : t.yes ? 2 : 3", 2.0),
                evaluates("t.no ? 1 : t.no ? 2 : 3", 3.0),
                evaluates("t.yes ? 1 : t.bomb() ? 2 : 3", 1.0),
                // nested in the true branch: a ? (b ? c : d) : e
                evaluates("t.yes ? t.no ? 1 : 2 : 3", 2.0),
                evaluates("t.yes ? t.yes ? 1 : 2 : 3", 1.0),
                evaluates("t.no ? t.yes ? 1 : 2 : 3", 3.0),
                // nested in the condition
                evaluates("(t.yes ? t.no : t.yes) ? 1 : 2", 2.0)
        );
    }

    @TestFactory
    @DisplayName("Works inside function arguments")
    Stream<DynamicTest> arguments() {
        return Stream.of(
                evaluates("math.pow(t.yes ? 2 : 5, 3)", 8.0),
                evaluates("math.pow(3, t.no ? 1 : 2)", 9.0),
                evaluates("math.pow(t.no ? 1 : 2, t.yes ? 2 : 5)", 4.0),
                evaluates("math.abs(t.no ? 1 : -4)", 4.0),
                evaluates("lib.iif(t.yes ? t.no : t.yes, 1, 2)", 2.0),
                evaluates("t.yes ? math.pow(2, 3) : math.pow(3, 2)", 8.0)
        );
    }

    @TestFactory
    @DisplayName("Evaluates only the selected branch, and the condition once")
    Stream<DynamicTest> laziness() {
        return Stream.of(
                dynamicTest("false branch not run", () -> {
                    assertEquals(7.0, this.eval("t.yes ? 7 : t.bomb()"));
                    assertEquals(0, this.bombCalls.get());
                }),
                dynamicTest("true branch not run", () -> {
                    assertEquals(8.0, this.eval("t.no ? t.bomb() : 8"));
                    assertEquals(0, this.bombCalls.get());
                }),
                dynamicTest("condition evaluated once", () -> {
                    assertEquals(1.0, this.eval("t.counted ? 1 : 2"));
                    assertEquals(1, this.conditionCalls.get());
                })
        );
    }

    @Test
    @DisplayName("Compiles to a conditional node")
    void compilesToConditional() {
        assertInstanceOf(Conditional.class, this.engine.compile("t.yes ? 1 : 2"));
    }

    @TestFactory
    @DisplayName("A constant condition selects the branch at compile time")
    Stream<DynamicTest> constantCondition() {
        return Stream.of(
                dynamicTest("true selects the first branch", () -> {
                    var expression = this.engine.compile("true ? 'x' : t.bomb()");
                    assertEquals("x", assertInstanceOf(Literal.class, expression).value());
                }),
                dynamicTest("false selects the second branch, unevaluated", () -> {
                    var expression = this.engine.compile("1 > 2 ? t.bomb() : t.text");
                    assertFalse(expression instanceof Conditional);
                    assertEquals(0, this.bombCalls.get());
                    assertEquals("abc", expression.eval());
                }),
                dynamicTest("a constant non-boolean converts", () ->
                        assertEquals("n", assertInstanceOf(Literal.class, this.engine.compile("0 ? 'y' : 'n'")).value())),
                dynamicTest("an all-constant expression folds", () ->
                        assertEquals(6.0, assertInstanceOf(Literal.class, this.engine.compile("(2 > 1 ? 3 : 4) * 2")).value()))
        );
    }

    @TestFactory
    @DisplayName("Same results as lib.iif")
    Stream<DynamicTest> sameAsIif() {
        return Stream.of(
                        "t.yes, 1, 2", "t.no, 1, 2", "t.one, 'a', 'b'", "t.text, 'a', 'b'", "t.nul, 'a', t.nul")
                .map(args -> {
                    var parts = args.split(", ");
                    var operator = "%s ? %s : %s".formatted(parts[0], parts[1], parts[2]);
                    return dynamicTest(operator, () -> assertEquals(this.eval("lib.iif(" + args + ")"), this.eval(operator)));
                });
    }

    @TestFactory
    @DisplayName("Double negation still simplifies through a conditional of booleans")
    Stream<DynamicTest> doubleNegation() {
        return Stream.of(
                dynamicTest("booleans", () ->
                        assertInstanceOf(Conditional.class, this.engine.compile("!!(t.one > 0 ? t.yes == t.yes : t.no == t.no)"))),
                dynamicTest("numbers", () ->
                        assertInstanceOf(Conditional.class, this.engine.compile("--(t.yes ? 1 * t.one : -t.one)")))
        );
    }
}
