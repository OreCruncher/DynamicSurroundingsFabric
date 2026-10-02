package org.orecruncher.dsurround.lib.scripting.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.ExecutionContext;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.scripting.VariableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Regression tests for the fixes made to the scripting engine and its runtime (ExecutionContext, Script,
 * library functions). Each case covers one specific bug, so a failure points directly at a regression.
 * <p>
 * Cases run against an {@link ExecutionContext} with a small test variable set ("v.*"). Scripts are evaluated
 * through {@link ExecutionContext#eval(Script)}, which reports errors by returning the error message, so error
 * cases assert on the returned text.
 */
@DisplayName("Scripting fixes")
public class FixesTest {

    private final List<String> logs = new ArrayList<>();
    private final IModLog logger = new IModLog() {
        @Override
        public boolean isDebugging() {
            return false;
        }

        @Override
        public boolean isTracing(int mask) {
            return false;
        }

        @Override
        public void log(Level level, Throwable t, String f, Object... a) {
            if (level == Level.ERROR)
                FixesTest.this.logs.add(String.format(f, a));
        }
    };

    private ExecutionContext context;
    private TestVariables vars;

    /**
     * Test variables and functions, all under the "v" namespace.
     */
    static class TestVariables extends VariableSet {
        final Object intVal = 1;
        final AtomicInteger bombCalls = new AtomicInteger();

        TestVariables(String name) {
            super(name);
        }

        @Override
        public void configure(IConfigureDefinition c) {
            c.defineVariable(id("int"), () -> this.intVal);   // an Integer, not a Double
            c.defineVariable(id("nul"), () -> null);
            c.defineVariable(id("five"), () -> "5");
            c.defineVariable(id("num"), () -> 5.0);
            c.defineVariable(id("nan"), () -> Double.NaN);
            c.defineVariable(id("str"), () -> "abc");
            c.defineVariable(id("obj"), Object::new);          // cannot convert to a boolean or number
            c.function(id("bomb")).handler(a -> {
                this.bombCalls.incrementAndGet();
                throw new RuntimeException("boom");
            });
            c.function(id("toInt")).param(ArgType.ANY).handler(a -> ScriptHelpers.toInteger(a.value(0)));
            c.function(id("keep")).param(ArgType.ANY).handler(a -> a);  // returns its arguments object
        }
    }

    @BeforeEach
    void setUp() {
        this.logs.clear();
        this.context = new ExecutionContext("test", this.logger);
        this.vars = new TestVariables("v");
        this.context.add(this.vars);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private Object eval(String script) {
        return this.context.eval(new Script(script)).orElse(null);
    }

    /**
     * The script evaluates to the expected value ({@code null} for an empty result).
     */
    private DynamicTest evaluates(String name, String script, Object expected) {
        return dynamicTest("%s: %s".formatted(name, script), () -> assertEquals(expected, this.eval(script)));
    }

    /**
     * The script fails and the returned error message contains the fragment.
     */
    private DynamicTest failsWith(String name, String script, String fragment) {
        return dynamicTest("%s: %s".formatted(name, script), () -> {
            var result = String.valueOf(this.eval(script));
            assertTrue(result.contains(fragment), () -> "Result [" + result + "] does not contain [" + fragment + "]");
        });
    }

    /**
     * A custom check.
     */
    private static DynamicTest check(String name, Object expected, Supplier<Object> actual) {
        return dynamicTest(name, () -> assertEquals(expected, actual.get()));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Engine
    // ---------------------------------------------------------------------------------------------------------

    @TestFactory
    @DisplayName("Prefix operators and parentheses after a comma")
    Stream<DynamicTest> prefixAfterComma() {
        return Stream.of(
                evaluates("unary minus", "math.pow(2, -1)", 0.5),
                evaluates("unary plus", "math.pow(2, +3)", 8.0),
                evaluates("NOT", "lib.iif(v.int, !v.nul, 1)", true),
                evaluates("parenthesized group", "math.pow(2, (1 + v.int + 1))", 8.0)
        );
    }

    @TestFactory
    @DisplayName("Double negation keeps the coerced type")
    Stream<DynamicTest> doubleNegation() {
        return Stream.of(
                evaluates("!! yields a boolean", "!!v.num", true),
                evaluates("!! compares as boolean", "!!v.num == true", true),
                evaluates("-- yields a number", "--v.five + 1", 6.0)
        );
    }

    @TestFactory
    @DisplayName("Equality compares numbers by value regardless of boxed type")
    Stream<DynamicTest> numericEquality() {
        return Stream.of(
                evaluates("Integer == Double", "v.int == 1", true),
                evaluates("Integer != Double", "v.int != 1", false),
                evaluates("lib.oneOf with an Integer", "lib.oneOf(v.int, 0, 1)", true),
                evaluates("lib.oneOf with a null testee", "lib.oneOf(v.nul, 0, 1)", false)
        );
    }

    @TestFactory
    @DisplayName("ScriptHelpers.toInteger")
    Stream<DynamicTest> toInteger() {
        return Stream.of(
                evaluates("accepts an integral script literal", "v.toInt(3)", 3),
                failsWith("rejects a fractional value", "v.toInt(2.5)", "not an integer")
        );
    }

    @TestFactory
    @DisplayName("Function arguments are not shared between evaluations")
    Stream<DynamicTest> argumentArrays() {
        return Stream.of(
                check("each evaluation gets its own arguments", true, () -> {
                    var script = new Script("v.keep(v.num)");
                    var first = this.context.eval(script).orElseThrow();
                    var second = this.context.eval(script).orElseThrow();
                    return first != second;
                })
        );
    }

    // ---------------------------------------------------------------------------------------------------------
    // Lexer, constant folding, and string conversion
    // ---------------------------------------------------------------------------------------------------------

    @TestFactory
    @DisplayName("Lexing and parsing")
    Stream<DynamicTest> lexingAndParsing() {
        return Stream.of(
                failsWith("member access on a string is rejected", "'abc'.x", "Unexpected character '.'"),
                failsWith("'=' error names '='", "1 = 2", "Unexpected character '='"),
                failsWith("'|' error names '|'", "true | x", "Unexpected character '|'"),
                failsWith("comma in a grouping parenthesis is rejected", "math.pow((1, 2))", "Comma outside"),
                check("error column is relative to its line", true,
                        () -> String.valueOf(this.eval("true &&\n  true &&\n    $")).startsWith("Script error: (3, 5)")),
                evaluates("nested calls", "math.pow(math.sqrt(9), 2)", 9.0),
                evaluates("zero-argument call", "math.random() < 1", true),
                evaluates("precedence", "1 + 2 * v.int * 3", 7.0)
        );
    }

    @TestFactory
    @DisplayName("Constant folding matches runtime behavior")
    Stream<DynamicTest> constantFolding() {
        return Stream.of(
                evaluates("negating a numeric string", "-'5'", -5.0),
                failsWith("0 * non-number is an error, not 0", "0 * 'abc'", "operand must be a number"),
                evaluates("0 * NaN is NaN", "0 * v.nan", Double.NaN),
                evaluates("two literals are summed", "2 + 3", 5.0),
                evaluates("negative zero plus a value", "-0 + v.num", 5.0)
        );
    }

    @TestFactory
    @DisplayName("String concatenation")
    Stream<DynamicTest> concatenation() {
        return Stream.of(
                evaluates("integral literal has no '.0'", "'Level ' + 3", "Level 3"),
                evaluates("integral variable has no '.0'", "'Level ' + v.num", "Level 5"),
                evaluates("fractional value keeps its decimals", "'x' + 2.5", "x2.5"),
                evaluates("null concatenates as 'null'", "v.nul + 'a'", "nulla")
        );
    }

    // ---------------------------------------------------------------------------------------------------------
    // Runtime (ExecutionContext, Script, library functions)
    // ---------------------------------------------------------------------------------------------------------

    @TestFactory
    @DisplayName("Null results and check()")
    Stream<DynamicTest> nullResultsAndCheck() {
        return Stream.of(
                check("a null result is an empty Optional", true,
                        () -> this.context.eval(new Script("v.nul")).isEmpty()),
                check("lib.iif selecting null is an empty Optional", true,
                        () -> this.context.eval(new Script("lib.iif(v.int == 1, v.nul, 2)")).isEmpty()),
                check("check() on a true script", true, () -> this.context.check(new Script("v.int == 1"))),
                check("check() on a false script", false, () -> this.context.check(new Script("v.int == 2")))
        );
    }

    @TestFactory
    @DisplayName("check() handles every kind of result")
    Stream<DynamicTest> checkResults() {
        return Stream.of(
                check("Boolean true", true, () -> this.context.check(new Script("v.num > 1"))),
                check("Boolean false", false, () -> this.context.check(new Script("v.num < 1"))),
                check("string 'true'", true, () -> this.context.check(new Script("'TRUE'"))),
                check("other string", false, () -> this.context.check(new Script("v.str"))),
                check("non-zero number", true, () -> this.context.check(new Script("v.num"))),
                check("zero", false, () -> this.context.check(new Script("v.num - 5"))),
                check("null", false, () -> this.context.check(new Script("v.nul"))),
                check("runtime error", false, () -> this.context.check(new Script("v.bomb()"))),
                check("script error", false, () -> this.context.check(new Script("v.str * 2"))),
                check("compile error", false, () -> this.context.check(new Script("v.undefined"))),
                check("unconvertible result is false and logged once", "false,1", () -> {
                    this.logs.clear();
                    var script = new Script("v.obj");
                    var result = false;
                    for (int i = 0; i < 10; i++)
                        result |= this.context.check(script);
                    return result + "," + this.logs.stream().filter(l -> l.contains("cannot be converted to a boolean")).count();
                })
        );
    }

    @TestFactory
    @DisplayName("Compiled scripts are tied to the context that compiled them")
    Stream<DynamicTest> perContextCompilation() {
        return Stream.of(
                check("the same Script evaluates in each context's environment", "ctx1,ctx2", () -> {
                    var c1 = new ExecutionContext("a", this.logger);
                    var c2 = new ExecutionContext("b", this.logger);
                    c1.configureScripting(d -> d.defineVariable("who", () -> "ctx1"));
                    c2.configureScripting(d -> d.defineVariable("who", () -> "ctx2"));
                    var script = new Script("who");
                    return c1.eval(script).orElseThrow() + "," + c2.eval(script).orElseThrow();
                }),
                check("a compile error is retried after definitions are added", 42.0, () -> {
                    var c = new ExecutionContext("late", this.logger);
                    var script = new Script("late.x");
                    c.eval(script);   // fails: late.x is not defined yet
                    c.configureScripting(d -> d.defineVariable("late.x", () -> 42.0));
                    return c.eval(script).orElseThrow();
                })
        );
    }

    @TestFactory
    @DisplayName("Runtime errors are logged once per script")
    Stream<DynamicTest> errorLogging() {
        return Stream.of(
                check("100 failing evaluations log one error", 1L, () -> {
                    this.logs.clear();
                    var script = new Script("v.bomb()");
                    for (int i = 0; i < 100; i++)
                        this.context.eval(script);
                    return this.logs.stream().filter(l -> l.contains("v.bomb()")).count();
                })
        );
    }

    @TestFactory
    @DisplayName("lib.iif only evaluates the selected branch")
    Stream<DynamicTest> lazyIif() {
        return Stream.of(
                check("true branch selected; false branch not run", 0, () -> {
                    this.vars.bombCalls.set(0);
                    assertEquals(7.0, this.eval("lib.iif(v.int == 1, 7, v.bomb())"));
                    return this.vars.bombCalls.get();
                }),
                check("false branch selected; true branch not run", 0, () -> {
                    this.vars.bombCalls.set(0);
                    assertEquals(8.0, this.eval("lib.iif(v.int == 2, v.bomb(), 8)"));
                    return this.vars.bombCalls.get();
                })
        );
    }

    @TestFactory
    @DisplayName("Library and math functions")
    Stream<DynamicTest> libraryFunctions() {
        return Stream.of(
                evaluates("lib.match", "lib.match('a.c', v.str)", true),
                evaluates("math.round with places", "math.round(math.pi, 2)", 3.14),
                evaluates("math.round without places", "math.round(math.pi)", 3.0),
                evaluates("math.round with negative places", "math.round(1234, -2)", 1200.0),
                failsWith("math.round with too many arguments is a compile error", "math.round(1, 2, 3)", "expected 1 to 2 but received 3")
        );
    }

    @TestFactory
    @DisplayName("Variable set registration")
    Stream<DynamicTest> variableSets() {
        return Stream.of(
                dynamicTest("a duplicate set name is rejected regardless of case", () -> {
                    var ex = assertThrows(IllegalStateException.class, () -> this.context.add(new TestVariables("V")));
                    assertTrue(ex.getMessage().contains("already defined"), ex.getMessage());
                })
        );
    }
}
