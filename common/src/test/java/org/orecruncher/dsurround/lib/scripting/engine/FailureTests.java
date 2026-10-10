package org.orecruncher.dsurround.lib.scripting.engine;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.ScriptArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that a {@link ScriptException} identifies what went wrong and exactly where in the script it happened.
 * <p>
 * Each case states the expected line and column <em>and</em> the text ("locus") that should be found at that
 * location in the script. For every case the test checks that:
 * <ol>
 *     <li>the exception is raised in the expected phase (compile or evaluation)</li>
 *     <li>the reason (message without the location prefix) matches</li>
 *     <li>the reported line and column match</li>
 *     <li>the reported offset points at the locus in the script text, so the carat shown in logs lands on the
 *     offending token rather than a neighbouring character</li>
 *     <li>the offset, line, and column agree with each other (computed independently from the script)</li>
 *     <li>the full message is formatted as {@code (line, column) reason}, or just the reason when the problem
 *     has no specific location</li>
 * </ol>
 * Checks 4 and 5 are what catch an off-by-one location: a message string copied from the engine's output
 * would pass check 3, but the locus would not be at the reported offset.
 */
public class FailureTests {

    private enum Phase {COMPILE, EVAL}

    /**
     * @param script Script to compile/evaluate
     * @param phase  Phase in which the exception is expected
     * @param line   Expected 1-based line, or -1 if the problem has no location
     * @param column Expected 1-based column, or -1 if the problem has no location
     * @param locus  Text expected at the reported location ("" for end of input)
     * @param reason Expected message without the location prefix
     */
    private record Failure(String script, Phase phase, int line, int column, String locus, String reason) {
    }

    private static Failure compile(String script, int line, int column, String locus, String reason) {
        return new Failure(script, Phase.COMPILE, line, column, locus, reason);
    }

    private static Failure eval(String script, int line, int column, String locus, String reason) {
        return new Failure(script, Phase.EVAL, line, column, locus, reason);
    }

    private static Failure unlocated(String script, String reason) {
        return new Failure(script, Phase.COMPILE, -1, -1, null, reason);
    }

    private static final String NOT_A_NUMBER_LEFT = "Left operand must be a number or value that converts to a number";
    private static final String NOT_A_NUMBER_RIGHT = "Right operand must be a number or value that converts to a number";

    public static final List<Failure> TEST_CASES = List.of(
            // ---- Lexer ------------------------------------------------------------------------------------------
            compile("1..0", 1, 2, ".", "Unexpected character '.'"),
            compile("4 % 2", 1, 3, "%", "Unexpected character '%'"),
            compile("a = b", 1, 3, "=", "Unexpected character '=' (did you mean '=='?)"),
            compile("true | false", 1, 6, "|", "Unexpected character '|' (did you mean '||'?)"),
            compile("true & false", 1, 6, "&", "Unexpected character '&' (did you mean '&&'?)"),
            compile("'abc'.length", 1, 6, ".", "Unexpected character '.'"),
            compile("1 + 'abc", 1, 5, "'abc", "Unterminated string"),
            compile("math.", 1, 6, "", "Unexpected end of line"),
            compile("math.1", 1, 6, "1", "Unexpected character '1'"),
            compile("test.bool &&\n    #", 2, 5, "#", "Unexpected character '#'"),
            compile("// comment\n1 +\n  @", 3, 3, "@", "Unexpected character '@'"),
            compile("'multi\nline' + $", 2, 9, "$", "Unexpected character '$'"),
            compile("true &&\r\n  #", 2, 3, "#", "Unexpected character '#'"),

            // ---- RPN conversion -------------------------------------------------------------------------------
            compile("1 +", 1, 3, "+", "Expected 2 operands, but found 1"),
            compile("(true || false) ||", 1, 17, "||", "Expected 2 operands, but found 1"),
            compile("math.cos(Math.toRadians(45)", 1, 9, "(", "Mismatched parentheses"),
            compile("math.DoesNotExist(12 * Math.PI)", 1, 18, "(", "Unexpected '(' (undefined function/typo?)"),
            compile("1 + 2)", 1, 6, ")", "Mismatched parenthesis: missing '('"),
            compile("bad.parmVarArgs(1)", 1, 1, "bad.parmVarArgs", "Mismatched variable arguments: expected at least 2 but received 1"),
            compile("bad.parm(1)", 1, 1, "bad.parm", "Mismatched variable arguments: expected 2 but received 1"),
            compile("bad.parm(1, 2, 3)", 1, 1, "bad.parm", "Mismatched variable arguments: expected 2 but received 3"),
            compile("1 + bad.parm(1)", 1, 5, "bad.parm", "Mismatched variable arguments: expected 2 but received 1"),
            compile("test.bool &&\n  math.abs(bad.parm(1, 2, 3))", 2, 12, "bad.parm", "Mismatched variable arguments: expected 2 but received 3"),
            compile("1, 2", 1, 2, ",", "Comma outside of valid function parameters"),
            compile("bad.parm((1, 2))", 1, 12, ",", "Comma outside of valid function parameters"),
            compile("test.bool !test.bool", 1, 11, "!", "Unexpected character '!'"),
            compile("lib.iif(\n  test.bool,\n  1)", 1, 1, "lib.iif", "Mismatched variable arguments: expected 3 but received 2"),
            // a value with no operator before it is reported where it starts
            compile("1 2", 1, 3, "2", "Unexpected '2' (missing operator?)"),
            compile("1 2 3", 1, 3, "2", "Unexpected '2' (missing operator?)"),
            compile("(1 + 2) 3", 1, 9, "3", "Unexpected '3' (missing operator?)"),
            compile("math.abs(1) test.bool", 1, 13, "test.bool", "Unexpected 'test.bool' (missing operator?)"),
            compile("1 math.abs(2) * 3", 1, 3, "math.abs", "Unexpected 'math.abs' (missing operator?)"),
            compile("1 +\n  2\n  3", 3, 3, "3", "Unexpected '3' (missing operator?)"),
            // malformed scripts whose RPN is well-formed (these used to compile)
            compile("|| 1 1", 1, 1, "||", "Unexpected '||' (missing left operand?)"),
            compile("test.bool test.bool &&", 1, 11, "test.bool", "Unexpected 'test.bool' (missing operator?)"),
            compile("(* 2 3)", 1, 2, "*", "Unexpected '*' (missing left operand?)"),
            compile("math.pow(, 2)", 1, 10, ",", "Unexpected ',' (missing argument?)"),
            compile("math.pow(2, , 3)", 1, 13, ",", "Unexpected ',' (missing argument?)"),
            compile("math.pow(2 *, 3)", 1, 13, ",", "Unexpected ',' (missing argument?)"),
            unlocated("()", "Logic not detected in script"),
            unlocated("", "Empty script"),
            unlocated("  \n  // nothing here", "Empty script"),

            // ---- Compilation (name resolution and constant folding) -------------------------------------------
            compile("undefined.var + 1", 1, 1, "undefined.var", "Unable to locate variable 'undefined.var'"),
            compile("1 +\n  missing", 2, 3, "missing", "Unable to locate variable 'missing'"),
            compile("1 + !45", 1, 3, "+", "Incompatible operands for operator '+'"),
            compile("0 * 'abc'", 1, 3, "*", NOT_A_NUMBER_RIGHT),
            compile("-true", 1, 1, "-", "Operand must be a number or value that converts to a number"),

            // ---- Evaluation -----------------------------------------------------------------------------------
            eval("test.bool + 1", 1, 11, "+", "Incompatible operands for operator '+'"),
            eval("test.str * 2", 1, 10, "*", NOT_A_NUMBER_LEFT),
            eval("2 / test.str", 1, 3, "/", NOT_A_NUMBER_RIGHT),
            eval("test.bool &&\n  test.str > 1", 2, 12, ">", NOT_A_NUMBER_LEFT),
            eval("-test.str", 1, 1, "-", "Operand must be a number or value that converts to a number"),
            eval("!test.obj", 1, 1, "!", "Operand must be a boolean or value that converts to a boolean"),
            eval("test.obj || test.bool", 1, 10, "||", "Left operand must be a boolean or value that converts to a boolean"),
            eval("test.broken + 1", 1, 1, "test.broken", "Value provided is not a number"),
            eval("test.toNumber('abc')", 1, 1, "test.toNumber", "Value provided is not a number"),
            // arguments are converted to the declared parameter type; problems are reported at the argument
            eval("1 +\n  math.sqrt(test.str)", 2, 13, "test.str", "math.sqrt: argument 1 must be a number"),
            // the innermost location wins when calls are nested
            eval("math.abs(test.toNumber('x'))", 1, 10, "test.toNumber", "Value provided is not a number"),
            eval("lib.iif(test.obj, 1, 2)", 1, 9, "test.obj", "lib.iif: argument 1 must be a boolean")
    );

    private static ScriptEngine createEngine() {
        var scriptEngine = new ScriptEngine();
        scriptEngine.function("bad.parmVarArgs").param(ArgType.ANY).param(ArgType.ANY).varParams(ArgType.ANY).handler(ScriptArguments::count);
        scriptEngine.function("bad.parm").param(ArgType.ANY).param(ArgType.ANY).handler(ScriptArguments::count);
        // ANY: the handler converts, so a failure comes from inside the handler and is located at the call
        scriptEngine.function("test.toNumber").param(ArgType.ANY).handler(a -> ScriptHelpers.toDouble(a.value(0)));
        scriptEngine.defineVariable("test.bool", () -> true);
        scriptEngine.defineVariable("test.str", () -> "abc");
        scriptEngine.defineVariable("test.obj", Object::new);
        scriptEngine.defineVariable("test.broken", () -> ScriptHelpers.toDouble("not a number"));
        return scriptEngine;
    }

    @TestFactory
    public Stream<DynamicTest> stringDynamicTests() {
        var scriptEngine = createEngine();
        return TEST_CASES.stream()
                .map(data -> DynamicTest.dynamicTest("Failure testing \"%s\"".formatted(printable(data.script())),
                        () -> validate(scriptEngine, data)));
    }

    private static void validate(ScriptEngine scriptEngine, Failure data) {
        var script = data.script();
        ScriptException exception;
        if (data.phase() == Phase.COMPILE) {
            exception = assertThrows(ScriptException.class, () -> scriptEngine.compile(script),
                    "Expected compilation to fail");
        } else {
            var expression = assertDoesNotThrow(() -> scriptEngine.compile(script),
                    "Expected compilation to succeed; the failure should occur during evaluation");
            exception = assertThrows(ScriptException.class, expression::eval, "Expected evaluation to fail");
        }

        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertEquals(data.reason(), exception.getReason(), "reason"));
        checks.add(() -> assertEquals(data.line(), exception.getLineNumber(), "line"));
        checks.add(() -> assertEquals(data.column(), exception.getColumnNumber(), "column"));

        if (data.line() < 0) {
            checks.add(() -> assertFalse(exception.hasLocation(), "should not have a location"));
            checks.add(() -> assertEquals(-1, exception.getPosition(), "position"));
            checks.add(() -> assertEquals(data.reason(), exception.getMessage(), "message"));
        } else {
            var position = exception.getPosition();
            checks.add(() -> assertTrue(exception.hasLocation(), "should have a location"));
            checks.add(() -> assertTrue(position >= 0 && position <= script.length(),
                    "position %d is outside the script (length %d)".formatted(position, script.length())));
            checks.add(() -> assertTrue(position >= 0 && position <= script.length() && script.startsWith(data.locus(), position),
                    () -> "expected [%s] at offset %d but found [%s]".formatted(
                            printable(data.locus()), position, printable(context(script, position)))));
            checks.add(() -> assertEquals(lineOf(script, position), exception.getLineNumber(),
                    "line is inconsistent with offset " + position));
            checks.add(() -> assertEquals(columnOf(script, position), exception.getColumnNumber(),
                    "column is inconsistent with offset " + position));
            checks.add(() -> assertEquals("(%d, %d) %s".formatted(data.line(), data.column(), data.reason()),
                    exception.getMessage(), "message"));
        }

        assertAll("\"%s\" -> %s".formatted(printable(script), exception.getMessage()), checks.stream());
    }

    /**
     * 1-based line containing the offset.
     */
    private static int lineOf(String script, int position) {
        int line = 1;
        for (int i = 0; i < Math.min(position, script.length()); i++) {
            if (script.charAt(i) == '\n')
                line++;
        }
        return line;
    }

    /**
     * 1-based column of the offset within its line.
     */
    private static int columnOf(String script, int position) {
        int lineStart = script.lastIndexOf('\n', position - 1) + 1;
        return position - lineStart + 1;
    }

    private static String context(String script, int position) {
        if (position < 0 || position > script.length())
            return "<out of range>";
        var end = Math.min(script.length(), position + 10);
        return script.substring(position, end);
    }

    private static String printable(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
