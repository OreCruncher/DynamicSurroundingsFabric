package org.orecruncher.dsurround.lib.scripting.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Call;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Conditional;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Literal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Tests for defining functions with {@link IConfigureDefinition#function(String)}: argument conversion, argument
 * count ranges, lazy and pure functions, and the shorthands.
 */
@DisplayName("Function definitions")
public class FunctionDefinitionTests {

    private ScriptEngine engine;
    private final AtomicInteger converterCalls = new AtomicInteger();
    private final AtomicInteger handlerCalls = new AtomicInteger();
    private final AtomicInteger argumentEvaluations = new AtomicInteger();
    private Object propertyValue;

    /** A custom type with a specific rejection reason, like a biome trait. */
    private final ArgType<String> color = ArgType.of("color", v -> {
        this.converterCalls.incrementAndGet();
        var name = String.valueOf(v);
        return switch (name) {
            case "red", "green" -> name.toUpperCase();
            default -> ArgType.reject("unknown color '%s'".formatted(name));
        };
    });

    @BeforeEach
    void setUp() {
        this.converterCalls.set(0);
        this.handlerCalls.set(0);
        this.argumentEvaluations.set(0);
        this.propertyValue = "first";

        var e = new ScriptEngine();
        e.defineVariable("t.text", () -> "abc");
        e.defineVariable("t.five", () -> "5");
        e.defineVariable("t.red", () -> "red");
        e.defineVariable("t.blue", () -> "blue");
        e.defineVariable("t.counted", () -> {
            this.argumentEvaluations.incrementAndGet();
            return 1.0;
        });

        e.function("t.num").param(ArgType.NUMBER).handler(args -> args.number(0) * 2);
        e.function("t.int").param(ArgType.INTEGER).handler(args -> args.integer(0));
        e.function("t.bool").param(ArgType.BOOLEAN).handler(args -> args.bool(0));
        e.function("t.str").param(ArgType.STRING).handler(args -> args.string(0));
        e.function("t.color").param(this.color).handler(args -> args.<String>get(0));
        e.function("t.opt").param(ArgType.NUMBER).optional(ArgType.NUMBER).handler(args -> args.count());
        e.function("t.var").param(ArgType.NUMBER).varParams(ArgType.NUMBER).handler(args -> args.count());
        e.function("t.pure").param(ArgType.NUMBER).pure().handler(args -> {
            this.handlerCalls.incrementAndGet();
            return args.number(0) + 1;
        });
        e.function("t.impure").param(ArgType.NUMBER).handler(args -> {
            this.handlerCalls.incrementAndGet();
            return args.number(0) + 1;
        });
        e.function("t.pureFails").param(ArgType.NUMBER).pure().handler(args -> ScriptHelpers.toDouble("x"));
        e.function("t.first").param(ArgType.ANY).param(ArgType.ANY).lazy().handler(args -> args.value(0));
        e.function("t.twice").param(ArgType.ANY).lazy().handler(args -> {
            args.value(0);
            return args.value(0);
        });
        e.property("t.prop", () -> this.propertyValue);
        e.numberFunction("t.half", x -> x / 2);
        e.numberFunction("t.sum", Double::sum);
        this.engine = e;
    }

    private Object eval(String script) {
        return this.engine.compile(script).eval();
    }

    private DynamicTest evaluates(String script, Object expected) {
        return dynamicTest("%s  =>  %s".formatted(script, expected), () -> assertEquals(expected, this.eval(script)));
    }

    // Note: @BeforeEach runs once per @TestFactory method, not once per dynamic test, so tests that check
    // counters reset them first.

    /**
     * The script fails at compile time or evaluation with the reason, located at the locus.
     */
    private DynamicTest fails(String script, String locus, String reason) {
        return dynamicTest("[%s]  fails with  %s".formatted(script, reason), () -> {
            var ex = assertThrows(ScriptException.class, () -> this.eval(script));
            assertEquals(reason, ex.getReason());
            assertTrue(ex.hasLocation(), "should have a location");
            assertTrue(script.startsWith(locus, ex.getPosition()),
                    () -> "expected [%s] at offset %d of [%s]".formatted(locus, ex.getPosition(), script));
        });
    }

    @TestFactory
    @DisplayName("Arguments are converted to the declared type")
    Stream<DynamicTest> conversion() {
        return Stream.of(
                evaluates("t.num(3)", 6.0),
                evaluates("t.num('3')", 6.0),
                evaluates("t.num(t.five)", 10.0),
                evaluates("t.int(2)", 2),
                evaluates("t.int('7')", 7),
                evaluates("t.bool(1)", true),
                evaluates("t.bool('false')", false),
                evaluates("t.str(5)", "5"),
                evaluates("t.str(2.5)", "2.5"),
                evaluates("t.color('red')", "RED"),
                evaluates("t.color(t.red)", "RED")
        );
    }

    @TestFactory
    @DisplayName("Invalid arguments are reported at the argument")
    Stream<DynamicTest> invalidArguments() {
        return Stream.of(
                // constants: compile errors
                fails("t.num('abc')", "'abc'", "t.num: argument 1 must be a number"),
                fails("t.int(2.5)", "2.5", "t.int: argument 1 must be a whole number"),
                fails("t.color('blue')", "'blue'", "t.color: unknown color 'blue'"),
                fails("1 + t.num(\n  'abc')", "'abc'", "t.num: argument 1 must be a number"),
                fails("t.opt(1, 'x')", "'x'", "t.opt: argument 2 must be a number"),
                // non-constants: evaluation errors, located at where the argument starts
                fails("t.num(t.text)", "t.text", "t.num: argument 1 must be a number"),
                fails("t.num(t.text + 1)", "t.text", "t.num: argument 1 must be a number"),
                fails("t.color(t.blue)", "t.blue", "t.color: unknown color 'blue'"),
                fails("t.var(1, 2, t.text)", "t.text", "t.var: argument 3 must be a number")
        );
    }

    @Test
    @DisplayName("Invalid constant arguments fail at compile time")
    void constantErrorsAtCompileTime() {
        var ex = assertThrows(ScriptException.class, () -> this.engine.compile("t.color('blue')"));
        assertEquals("(1, 9) t.color: unknown color 'blue'", ex.getMessage());
    }

    @Test
    @DisplayName("Constant arguments are converted once, not on every evaluation")
    void constantsConvertedOnce() {
        this.converterCalls.set(0);
        var constant = this.engine.compile("t.color('red')");
        for (int i = 0; i < 5; i++)
            constant.eval();
        assertEquals(1, this.converterCalls.get());

        this.converterCalls.set(0);
        var variable = this.engine.compile("t.color(t.red)");
        for (int i = 0; i < 5; i++)
            variable.eval();
        assertEquals(5, this.converterCalls.get());
    }

    @TestFactory
    @DisplayName("Argument counts")
    Stream<DynamicTest> argumentCounts() {
        return Stream.of(
                evaluates("t.opt(1)", 1),
                evaluates("t.opt(1, 2)", 2),
                fails("t.opt()", "t.opt", "Mismatched variable arguments: expected 1 to 2 but received 0"),
                fails("t.opt(1, 2, 3)", "t.opt", "Mismatched variable arguments: expected 1 to 2 but received 3"),
                evaluates("t.var(1)", 1),
                evaluates("t.var(1, 2, 3, 4)", 4),
                fails("t.var()", "t.var", "Mismatched variable arguments: expected at least 1 but received 0"),
                fails("t.num(1, 2)", "t.num", "Mismatched variable arguments: expected 1 but received 2"),
                fails("t.prop(1)", "t.prop", "Mismatched variable arguments: expected 0 but received 1")
        );
    }

    @TestFactory
    @DisplayName("Lazy functions evaluate arguments only on request, and only once")
    Stream<DynamicTest> lazy() {
        return Stream.of(
                dynamicTest("unrequested argument is not evaluated", () -> {
                    this.argumentEvaluations.set(0);
                    assertEquals("x", this.eval("t.first('x', t.counted)"));
                    assertEquals(0, this.argumentEvaluations.get());
                }),
                dynamicTest("requested twice, evaluated once", () -> {
                    this.argumentEvaluations.set(0);
                    assertEquals(1.0, this.eval("t.twice(t.counted)"));
                    assertEquals(1, this.argumentEvaluations.get());
                }),
                dynamicTest("lib.iif skips the other branch", () -> {
                    this.argumentEvaluations.set(0);
                    assertEquals(1.0, this.eval("lib.iif(true, t.counted, t.counted)"));
                    assertEquals(1, this.argumentEvaluations.get());
                })
        );
    }

    @TestFactory
    @DisplayName("Pure functions with constant arguments are evaluated at compile time")
    Stream<DynamicTest> pure() {
        return Stream.of(
                dynamicTest("folded to a literal and evaluated once", () -> {
                    this.handlerCalls.set(0);
                    var expression = this.engine.compile("t.pure(1)");
                    assertInstanceOf(Literal.class, expression);
                    for (int i = 0; i < 3; i++)
                        assertEquals(2.0, expression.eval());
                    assertEquals(1, this.handlerCalls.get());
                }),
                dynamicTest("not folded with a non-constant argument", () ->
                        assertInstanceOf(Call.class, this.engine.compile("t.pure(t.counted)"))),
                dynamicTest("functions not marked pure are not folded", () -> {
                    this.handlerCalls.set(0);
                    assertInstanceOf(Call.class, this.engine.compile("t.impure(1)"));
                    assertEquals(0, this.handlerCalls.get());
                }),
                dynamicTest("math.random is not folded", () ->
                        assertInstanceOf(Call.class, this.engine.compile("math.random()"))),
                dynamicTest("nested pure calls fold completely", () ->
                        assertInstanceOf(Literal.class, this.engine.compile("math.cos(math.toRadians(45)) == math.sqrt(2) / 2"))),
                dynamicTest("a failing pure call is a compile error at the call", () -> {
                    var ex = assertThrows(ScriptException.class, () -> this.engine.compile("1 + t.pureFails(1)"));
                    assertEquals("(1, 5) Value provided is not a number", ex.getMessage());
                })
        );
    }

    @TestFactory
    @DisplayName("Constants and specialized calls")
    Stream<DynamicTest> constantsAndSpecializedCalls() {
        return Stream.of(
                dynamicTest("math.pi is compiled as a constant", () ->
                        assertInstanceOf(Literal.class, this.engine.compile("math.pi"))),
                dynamicTest("expressions using constants fold", () -> {
                    var expression = this.engine.compile("math.pi * 2 == math.tau");
                    assertInstanceOf(Literal.class, expression);
                    assertEquals(true, expression.eval());
                }),
                dynamicTest("lib.iif compiles to a conditional node", () ->
                        assertInstanceOf(Conditional.class, this.engine.compile("lib.iif(t.counted > 0, 1, 2)"))),
                dynamicTest("lib.iif with a constant condition selects the branch at compile time", () -> {
                    this.argumentEvaluations.set(0);
                    var expression = this.engine.compile("lib.iif(false, t.counted, t.text)");
                    assertEquals("abc", expression.eval());
                    assertEquals(0, this.argumentEvaluations.get());
                }),
                dynamicTest("lib.iif converts a non-boolean condition", () ->
                        assertEquals("yes", this.eval("lib.iif(t.counted, 'yes', 'no')")))
        );
    }

    @TestFactory
    @DisplayName("Shorthands")
    Stream<DynamicTest> shorthands() {
        return Stream.of(
                dynamicTest("property reads the current value", () -> {
                    var expression = this.engine.compile("t.prop()");
                    assertEquals("first", expression.eval());
                    this.propertyValue = "second";
                    assertEquals("second", expression.eval());
                }),
                evaluates("t.half(5)", 2.5),
                evaluates("t.sum(2, 3)", 5.0),
                dynamicTest("numberFunction is pure", () -> assertInstanceOf(Literal.class, this.engine.compile("t.half(5)")))
        );
    }

    @TestFactory
    @DisplayName("lib.match")
    Stream<DynamicTest> match() {
        return Stream.of(
                evaluates("lib.match('a.c', t.text)", true),
                evaluates("lib.match('^b', t.text)", false),
                fails("lib.match('[a', t.text)", "'[a'",
                        "lib.match: invalid regular expression '[a' (Unclosed character class)"),
                fails("lib.match(t.text + '[', 'x')", "t.text",
                        "lib.match: invalid regular expression 'abc[' (Unclosed character class)")
        );
    }

    @TestFactory
    @DisplayName("Signatures")
    Stream<DynamicTest> signatures() {
        return Stream.of(
                dynamicTest("optional parameter", () -> assertEquals("math.round(number, [whole number])",
                        this.engine.environment.getFunction(Token.from(TokenType.IDENTIFIER, "math.round", null, 1, 0, 0)).signature())),
                dynamicTest("variable parameters", () -> assertEquals("lib.oneOf(value, value, value...)",
                        this.engine.environment.getFunction(Token.from(TokenType.IDENTIFIER, "lib.oneOf", null, 1, 0, 0)).signature()))
        );
    }

    @TestFactory
    @DisplayName("Builder rejects invalid parameter orders")
    Stream<DynamicTest> builderValidation() {
        return Stream.of(
                dynamicTest("required after optional", () -> assertThrows(IllegalStateException.class,
                        () -> this.engine.function("x.a").optional(ArgType.ANY).param(ArgType.ANY))),
                dynamicTest("optional after variable", () -> assertThrows(IllegalStateException.class,
                        () -> this.engine.function("x.b").varParams(ArgType.ANY).optional(ArgType.ANY))),
                dynamicTest("variable after optional", () -> assertThrows(IllegalStateException.class,
                        () -> this.engine.function("x.c").optional(ArgType.ANY).varParams(ArgType.ANY)))
        );
    }
}
