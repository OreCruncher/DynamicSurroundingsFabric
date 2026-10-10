package org.orecruncher.dsurround.lib.scripting.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.ScriptFunction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Unit tests for {@link RpnConverter}. Each test lexes a script, converts it to RPN, and compares a compact
 * rendering of the output:
 * <ul>
 *     <li>operands and operators are rendered by lexeme (unary negation is rendered as {@code neg} so that it
 *     can be told apart from binary minus)</li>
 *     <li>function calls are rendered as {@code name/argCount}</li>
 * </ul>
 * Table-driven cases are generated with {@link TestFactory}; each row is a {@code {script, expected}} pair,
 * where {@code expected} is the RPN rendering for conversions or a message fragment for failures.
 * <p>
 * Test environment functions: {@code f0()}, {@code f1(a)}, {@code f2(a, b)}, and {@code fv(a, ...)} (varargs,
 * minimum 1). No variables are defined because RpnConverter does not resolve identifiers.
 */
@DisplayName("RpnConverter")
class RpnConverterTests {

    private Environment environment;

    @BeforeEach
    void setUp() {
        this.environment = new Environment();
        this.environment.defineFunction(function("f0", 0, false));
        this.environment.defineFunction(function("f1", 1, false));
        this.environment.defineFunction(function("f2", 2, false));
        this.environment.defineFunction(function("fv", 1, true));
    }

    private static ScriptFunction function(String name, int arity, boolean hasVarArgs) {
        var parameters = java.util.Collections.<ArgType<?>>nCopies(arity, ArgType.ANY);
        return new ScriptFunction(name, parameters, arity, hasVarArgs ? ArgType.ANY : null, false, false, args -> 0);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private List<RpnConverter.RpnToken> convert(String script) {
        var tokens = new Lexer(script).getTokens();
        return new RpnConverter(this.environment).infixToRpn(tokens);
    }

    private String rpn(String script) {
        return this.convert(script).stream()
                .map(RpnConverterTests::render)
                .collect(Collectors.joining(" "));
    }

    private static String render(RpnConverter.RpnToken token) {
        if (token.isFunction())
            return token.token().lexeme() + "/" + token.argCount();
        if (token.token().type() == TokenType.NEG)
            return "neg";
        return token.token().lexeme();
    }

    private ScriptException assertConversionFails(String script, String expectedMessageFragment) {
        var ex = assertThrows(ScriptException.class, () -> this.convert(script),
                () -> "Expected conversion of [" + script + "] to fail");
        assertTrue(ex.getMessage().contains(expectedMessageFragment),
                () -> "Message [" + ex.getMessage() + "] does not contain [" + expectedMessageFragment + "]");
        return ex;
    }

    /**
     * Generates one test per {@code {script, expectedRpn}} row asserting the conversion output.
     */
    private Stream<DynamicTest> converts(String[]... cases) {
        return Arrays.stream(cases).map(c -> {
            var script = c[0];
            var expected = c[1];
            return dynamicTest("%s  =>  %s".formatted(script, expected),
                    () -> assertEquals(expected, this.rpn(script)));
        });
    }

    /**
     * Generates one test per {@code {script, messageFragment}} row asserting the conversion fails.
     */
    private Stream<DynamicTest> fails(String[]... cases) {
        return Arrays.stream(cases).map(c -> {
            var script = c[0];
            var message = c[1];
            return dynamicTest("[%s]  fails with  %s".formatted(script.replace("\n", "\\n"), message),
                    () -> this.assertConversionFails(script, message));
        });
    }

    private static String[] row(String script, String expected) {
        return new String[]{script, expected};
    }

    // ---------------------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Operands")
    class Operands {

        @TestFactory
        @DisplayName("Single operands pass through unchanged")
        Stream<DynamicTest> singleOperand() {
            return converts(
                    row("42", "42"),
                    row("3.5", "3.5"),
                    row("'text'", "'text'"),
                    row("true", "true"),
                    row("FALSE", "FALSE"),
                    row("x", "x"),
                    row("dim.id", "dim.id"),
                    row("((x))", "x")
            );
        }

        @Test
        @DisplayName("Identifiers are not resolved during conversion")
        void identifiersNotResolved() {
            assertEquals("undefined.name 1 +", rpn("undefined.name + 1"));
        }

        @Test
        @DisplayName("Operand tokens are not flagged as functions")
        void operandFlags() {
            var result = convert("x + 1");
            assertEquals(3, result.size());
            assertFalse(result.get(0).isFunction());
            assertEquals(0, result.get(0).argCount());
            assertFalse(result.get(2).isFunction());
        }
    }

    @Nested
    @DisplayName("Binary operators")
    class BinaryOperators {

        @TestFactory
        @DisplayName("Each binary operator")
        Stream<DynamicTest> eachOperator() {
            return converts(
                    row("1 + 2", "1 2 +"),
                    row("1 - 2", "1 2 -"),
                    row("1 * 2", "1 2 *"),
                    row("1 / 2", "1 2 /"),
                    row("1 == 2", "1 2 =="),
                    row("1 != 2", "1 2 !="),
                    row("1 < 2", "1 2 <"),
                    row("1 <= 2", "1 2 <="),
                    row("1 > 2", "1 2 >"),
                    row("1 >= 2", "1 2 >="),
                    row("x && y", "x y &&"),
                    row("x || y", "x y ||")
            );
        }

        @TestFactory
        @DisplayName("Precedence")
        Stream<DynamicTest> precedence() {
            return converts(
                    // multiplicative binds tighter than additive
                    row("1 + 2 * 3", "1 2 3 * +"),
                    row("1 * 2 + 3", "1 2 * 3 +"),
                    row("1 - 6 / 3", "1 6 3 / -"),
                    // additive binds tighter than relational
                    row("1 + 2 < 4", "1 2 + 4 <"),
                    // relational binds tighter than equality
                    row("1 < 2 == true", "1 2 < true =="),
                    // equality binds tighter than &&
                    row("x == 1 && y == 2", "x 1 == y 2 == &&"),
                    // && binds tighter than ||
                    row("x || y && z", "x y z && ||"),
                    row("x && y || z", "x y && z ||")
            );
        }

        @TestFactory
        @DisplayName("Left associativity")
        Stream<DynamicTest> leftAssociative() {
            return converts(
                    row("1 - 2 - 3", "1 2 - 3 -"),
                    row("8 / 4 / 2", "8 4 / 2 /"),
                    row("1 + 2 - 3 + 4", "1 2 + 3 - 4 +"),
                    row("x || y || z", "x y || z ||")
            );
        }

        @TestFactory
        @DisplayName("Parentheses override precedence")
        Stream<DynamicTest> parenthesesOverridePrecedence() {
            return converts(
                    row("(1 + 2) * 3", "1 2 + 3 *"),
                    row("1 - (2 - 3)", "1 2 3 - -"),
                    row("(x || y) && z", "x y || z &&"),
                    row("((1 + 2) * (3 + 4))", "1 2 + 3 4 + *")
            );
        }
    }

    @Nested
    @DisplayName("Unary operators")
    class UnaryOperators {

        @TestFactory
        @DisplayName("Prefix operators")
        Stream<DynamicTest> unary() {
            return converts(
                    row("-x", "x neg"),
                    row("!x", "x !"),
                    // unary plus is dropped by the lexer
                    row("+x", "x"),
                    row("1 + +2", "1 2 +"),
                    // unary binds tighter than any binary operator
                    row("-x * 2", "x neg 2 *"),
                    row("!x && y", "x ! y &&"),
                    row("!x == y", "x ! y =="),
                    // unary after a binary operator
                    row("2 * -x", "2 x neg *"),
                    row("1 - -1", "1 1 neg -"),
                    row("x && !y", "x y ! &&"),
                    // unary operators are right associative
                    row("!!x", "x ! !"),
                    row("- -x", "x neg neg"),
                    row("-!x", "x ! neg"),
                    // unary applied to a group
                    row("-(1 + 2)", "1 2 + neg"),
                    row("!(x || y)", "x y || !")
            );
        }
    }

    @Nested
    @DisplayName("Function calls")
    class FunctionCalls {

        @TestFactory
        @DisplayName("Arity")
        Stream<DynamicTest> arity() {
            return converts(
                    row("f0()", "f0/0"),
                    row("f1(x)", "x f1/1"),
                    row("f2(x, y)", "x y f2/2"),
                    row("fv(1)", "1 fv/1"),
                    row("fv(1, 2, 3, 4)", "1 2 3 4 fv/4"),
                    // function names are case-insensitive; the lexeme is preserved
                    row("F1(x)", "x F1/1")
            );
        }

        @TestFactory
        @DisplayName("Calls within expressions")
        Stream<DynamicTest> expressionsAndCalls() {
            return converts(
                    row("f2(1 + 2, x * y)", "1 2 + x y * f2/2"),
                    row("f1(x) + f1(y)", "x f1/1 y f1/1 +"),
                    row("1 + f1(x) * 2", "1 x f1/1 2 * +"),
                    row("-f1(x)", "x f1/1 neg"),
                    row("!f0()", "f0/0 !"),
                    row("f0() + 1", "f0/0 1 +"),
                    row("f1((x))", "x f1/1")
            );
        }

        @TestFactory
        @DisplayName("Nested calls keep separate argument counts")
        Stream<DynamicTest> nestedCalls() {
            return converts(
                    row("f1(f1(x))", "x f1/1 f1/1"),
                    row("f2(f1(x), y)", "x f1/1 y f2/2"),
                    row("f2(x, f1(y))", "x y f1/1 f2/2"),
                    row("f2(f0(), f1(x))", "f0/0 x f1/1 f2/2"),
                    row("f2(f0(), 1)", "f0/0 1 f2/2"),
                    row("fv(f2(1, 2), 3)", "1 2 f2/2 3 fv/2"),
                    row("f2(fv(1, 2, 3), f0())", "1 2 3 fv/3 f0/0 f2/2")
            );
        }

        @TestFactory
        @DisplayName("Prefix operators and groups as arguments")
        Stream<DynamicTest> prefixOperatorsInArguments() {
            return converts(
                    // first argument
                    row("f1(-x)", "x neg f1/1"),
                    row("f1(!x)", "x ! f1/1"),
                    row("f1((x + 1))", "x 1 + f1/1"),
                    // after a comma
                    row("f2(x, -1)", "x 1 neg f2/2"),
                    row("f2(x, +1)", "x 1 f2/2"),
                    row("f2(x, !y)", "x y ! f2/2"),
                    row("f2(x, (y + 1))", "x y 1 + f2/2"),
                    row("fv(1, -2, !x, (3))", "1 2 neg x ! 3 fv/4")
            );
        }

        @Test
        @DisplayName("Function tokens carry their argument count")
        void functionTokenFlags() {
            var result = convert("f2(x, y)");
            var call = result.getLast();
            assertTrue(call.isFunction());
            assertEquals(2, call.argCount());
            assertEquals("f2", call.token().lexeme());
        }

        @Test
        @DisplayName("Zero-argument function token has argument count 0")
        void zeroArgFunctionToken() {
            var result = convert("f0()");
            assertEquals(1, result.size());
            assertTrue(result.getFirst().isFunction());
            assertEquals(0, result.getFirst().argCount());
        }
    }

    @Nested
    @DisplayName("Errors")
    class Errors {

        @TestFactory
        @DisplayName("Empty script")
        Stream<DynamicTest> emptyScript() {
            return fails(
                    row("", "Empty script"),
                    row("   ", "Empty script"),
                    row("// just a comment", "Empty script")
            );
        }

        @TestFactory
        @DisplayName("Mismatched parentheses")
        Stream<DynamicTest> mismatchedParentheses() {
            return fails(
                    row("(1 + 2", "Mismatched parentheses"),
                    row("f1(x", "Mismatched parentheses"),
                    row("((x)", "Mismatched parentheses"),
                    row("1 + 2)", "missing '('"),
                    row("f1(x))", "missing '('"),
                    row(")", "Mismatched parenthesis"),
                    row("()", "Logic not detected")
            );
        }

        @TestFactory
        @DisplayName("Wrong argument count")
        Stream<DynamicTest> wrongArgumentCount() {
            return fails(
                    row("f0(1)", "expected 0 but received 1"),
                    row("f1()", "expected 1 but received 0"),
                    row("f1(1, 2)", "expected 1 but received 2"),
                    row("f2(1)", "expected 2 but received 1"),
                    row("f2(1, 2, 3)", "expected 2 but received 3"),
                    row("fv()", "expected at least 1 but received 0")
            );
        }

        @TestFactory
        @DisplayName("Misplaced comma")
        Stream<DynamicTest> misplacedComma() {
            return fails(
                    row("1, 2", "Comma outside"),
                    row("(1, 2)", "Comma outside"),
                    // a comma inside a grouping parenthesis does not add an argument to the enclosing call
                    row("f2((1, 2))", "Comma outside"),
                    row("f1(x, (1, 2))", "Comma outside"),
                    // every comma must follow a complete argument
                    row("f2(, 1)", "Unexpected ',' (missing argument?)"),
                    row("fv(1, , 2)", "Unexpected ',' (missing argument?)"),
                    row("f2(1 +, 2)", "Unexpected ',' (missing argument?)")
            );
        }

        @TestFactory
        @DisplayName("Unexpected tokens")
        Stream<DynamicTest> unexpectedTokens() {
            return fails(
                    row("x (1)", "Unexpected '('"),
                    row("notAFunction(1)", "Unexpected '('"),
                    row("1 (2)", "Unexpected '('"),
                    row("x !y", "Unexpected character '!'"),
                    row("f1(x) !y", "Unexpected character '!'")
            );
        }

        @TestFactory
        @DisplayName("Malformed expressions")
        Stream<DynamicTest> malformedExpressions() {
            return fails(
                    row("1 +", "Expected 2 operands, but found 1"),
                    row("* 2", "Unexpected '*' (missing left operand?)"),
                    row("|| 1 1", "Unexpected '||' (missing left operand?)"),
                    row("(* 2 3)", "Unexpected '*' (missing left operand?)"),
                    row("f2(1,)", "Expected 2 operands, but found 1"),
                    row("-", "Insufficient operands for unary operator"),
                    row("!", "Insufficient operands for unary operator"),
                    row("1 2", "Unexpected '2' (missing operator?)"),
                    row("f1(x) y", "Unexpected 'y' (missing operator?)"),
                    row("1 + 2 3", "Unexpected '3' (missing operator?)"),
                    row("f1(x) f1(y)", "Unexpected 'f1' (missing operator?)"),
                    row("x y &&", "Unexpected 'y' (missing operator?)")
            );
        }

        @Test
        @DisplayName("Errors report the line and 1-based column of the offending token; argument count errors point at the function name")
        void errorLocation() {
            var ex = assertConversionFails("1 + 2)", "missing '('");
            assertTrue(ex.getMessage().startsWith("(1, 6)"), ex.getMessage());

            ex = assertConversionFails("x &&\n   f2(1)", "expected 2 but received 1");
            assertTrue(ex.getMessage().startsWith("(2, 4)"), ex.getMessage());

            ex = assertConversionFails("f1(x) y", "Unexpected 'y' (missing operator?)");
            assertTrue(ex.getMessage().startsWith("(1, 7)"), ex.getMessage());
        }
    }
}