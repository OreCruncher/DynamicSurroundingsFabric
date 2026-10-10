package org.orecruncher.dsurround.lib.scripting.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Unit tests for {@link Lexer}. Table-driven cases are generated with {@link TestFactory}.
 * <p>
 * Token streams are compared using a compact rendering, with the trailing EOF token omitted:
 * <ul>
 *     <li>{@code NUM(1.0)} - NUMBER with its Double literal</li>
 *     <li>{@code STR(abc)} - STRING with its literal value (quotes removed, newlines shown as \n)</li>
 *     <li>{@code ID(dim.id)} - IDENTIFIER with its lexeme</li>
 *     <li>anything else - the TokenType name, such as {@code PLUS} or {@code NEG}</li>
 * </ul>
 */
@DisplayName("Lexer")
class LexerTests {

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private static List<Token> lex(String script) {
        return new Lexer(script).getTokens();
    }

    private static String render(String script) {
        var tokens = lex(script);
        return tokens.stream()
                .filter(t -> t.type() != TokenType.EOF)
                .map(LexerTests::render)
                .collect(Collectors.joining(" "));
    }

    private static String render(Token token) {
        return switch (token.type()) {
            case NUMBER -> "NUM(" + token.literal() + ")";
            case STRING -> "STR(" + token.literal().toString().replace("\n", "\\n") + ")";
            case IDENTIFIER -> "ID(" + token.lexeme() + ")";
            default -> token.type().name();
        };
    }

    private static String printable(String script) {
        return script.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private static ScriptException assertLexFails(String script, String expectedMessageFragment) {
        var ex = assertThrows(ScriptException.class, () -> lex(script),
                () -> "Expected lexing of [" + printable(script) + "] to fail");
        assertTrue(ex.getMessage().contains(expectedMessageFragment),
                () -> "Message [" + ex.getMessage() + "] does not contain [" + expectedMessageFragment + "]");
        return ex;
    }

    /**
     * Generates one test per {@code {script, expectedTokens}} row.
     */
    private static Stream<DynamicTest> lexes(String[]... cases) {
        return Arrays.stream(cases).map(c -> {
            var script = c[0];
            var expected = c[1];
            return dynamicTest("[%s]  =>  %s".formatted(printable(script), expected),
                    () -> assertEquals(expected, render(script)));
        });
    }

    /**
     * Generates one test per {@code {script, messageFragment}} row asserting that lexing fails.
     */
    private static Stream<DynamicTest> fails(String[]... cases) {
        return Arrays.stream(cases).map(c -> {
            var script = c[0];
            var message = c[1];
            return dynamicTest("[%s]  fails with  %s".formatted(printable(script), message),
                    () -> assertLexFails(script, message));
        });
    }

    private static String[] row(String script, String expected) {
        return new String[]{script, expected};
    }

    /**
     * Expected location of the token at {@code index} in the token list for {@code script}.
     */
    private record Location(String script, int index, String lexeme, int line, int column, int position) {
    }

    private static Location at(String script, int index, String lexeme, int line, int column, int position) {
        return new Location(script, index, lexeme, line, column, position);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("End of input")
    class EndOfInput {

        @TestFactory
        @DisplayName("Input with no tokens produces only EOF")
        Stream<DynamicTest> onlyEof() {
            return Stream.of("", " ", "\t\r\n", "// comment", "  // comment\n  ")
                    .map(script -> dynamicTest("[%s]".formatted(printable(script)), () -> {
                        var tokens = lex(script);
                        assertEquals(1, tokens.size());
                        assertEquals(TokenType.EOF, tokens.getFirst().type());
                    }));
        }

        @TestFactory
        @DisplayName("EOF is always the single last token")
        Stream<DynamicTest> eofLast() {
            return Stream.of("x", "1 + 2", "f(a, b)", "'s'", "x // trailing comment")
                    .map(script -> dynamicTest("[%s]".formatted(script), () -> {
                        var tokens = lex(script);
                        assertEquals(TokenType.EOF, tokens.getLast().type());
                        assertEquals(1L, tokens.stream().filter(t -> t.type() == TokenType.EOF).count());
                        assertEquals("", tokens.getLast().lexeme());
                    }));
        }
    }

    @Nested
    @DisplayName("Punctuation and operators")
    class Operators {

        @TestFactory
        @DisplayName("Punctuation")
        Stream<DynamicTest> punctuation() {
            return lexes(
                    row("(", "LEFT_PAREN"),
                    row(")", "RIGHT_PAREN"),
                    row(",", "COMMA"),
                    row("(,)", "LEFT_PAREN COMMA RIGHT_PAREN")
            );
        }

        @TestFactory
        @DisplayName("Binary operators")
        Stream<DynamicTest> binary() {
            return lexes(
                    row("1 + 2", "NUM(1.0) PLUS NUM(2.0)"),
                    row("1 - 2", "NUM(1.0) MINUS NUM(2.0)"),
                    row("1 * 2", "NUM(1.0) STAR NUM(2.0)"),
                    row("1 / 2", "NUM(1.0) SLASH NUM(2.0)"),
                    row("1 == 2", "NUM(1.0) EQUAL_EQUAL NUM(2.0)"),
                    row("1 != 2", "NUM(1.0) NOT_EQUAL NUM(2.0)"),
                    row("1 < 2", "NUM(1.0) LESS NUM(2.0)"),
                    row("1 <= 2", "NUM(1.0) LESS_EQUAL NUM(2.0)"),
                    row("1 > 2", "NUM(1.0) GREATER NUM(2.0)"),
                    row("1 >= 2", "NUM(1.0) GREATER_EQUAL NUM(2.0)"),
                    row("a && b", "ID(a) CONDITIONAL_AND ID(b)"),
                    row("a || b", "ID(a) CONDITIONAL_OR ID(b)")
            );
        }

        @TestFactory
        @DisplayName("Whitespace between tokens is optional")
        Stream<DynamicTest> noWhitespace() {
            return lexes(
                    row("1+2", "NUM(1.0) PLUS NUM(2.0)"),
                    row("x<=y", "ID(x) LESS_EQUAL ID(y)"),
                    row("x>=y", "ID(x) GREATER_EQUAL ID(y)"),
                    row("x!=y", "ID(x) NOT_EQUAL ID(y)"),
                    row("a&&b||c", "ID(a) CONDITIONAL_AND ID(b) CONDITIONAL_OR ID(c)"),
                    row("f(a,b)", "ID(f) LEFT_PAREN ID(a) COMMA ID(b) RIGHT_PAREN"),
                    row("x/y", "ID(x) SLASH ID(y)")
            );
        }

        @TestFactory
        @DisplayName("Two-character operators are only formed from adjacent characters")
        Stream<DynamicTest> splitOperators() {
            return fails(
                    row("x < = y", "Unexpected character '='"),
                    row("x ! = y", "Unexpected character '='"),
                    row("x = = y", "Unexpected character '='"),
                    row("x & & y", "Unexpected character '&'"),
                    row("x | | y", "Unexpected character '|'")
            );
        }
    }

    @Nested
    @DisplayName("Unary operator disambiguation")
    class Unary {

        @TestFactory
        @DisplayName("'-' is negation in prefix position and subtraction otherwise")
        Stream<DynamicTest> minus() {
            return lexes(
                    // prefix position: start of input, after an operator, after '(' or ','
                    row("-1", "NEG NUM(1.0)"),
                    row("-x", "NEG ID(x)"),
                    row("--1", "NEG NEG NUM(1.0)"),
                    row("1 - -1", "NUM(1.0) MINUS NEG NUM(1.0)"),
                    row("x * -1", "ID(x) STAR NEG NUM(1.0)"),
                    row("!-1", "NOT NEG NUM(1.0)"),
                    row("(-1)", "LEFT_PAREN NEG NUM(1.0) RIGHT_PAREN"),
                    row("f(1, -1)", "ID(f) LEFT_PAREN NUM(1.0) COMMA NEG NUM(1.0) RIGHT_PAREN"),
                    // after an operand: subtraction
                    row("x-1", "ID(x) MINUS NUM(1.0)"),
                    row("1-1", "NUM(1.0) MINUS NUM(1.0)"),
                    row("(x)-1", "LEFT_PAREN ID(x) RIGHT_PAREN MINUS NUM(1.0)"),
                    row("'a' - 1", "STR(a) MINUS NUM(1.0)"),
                    row("true - 1", "TRUE MINUS NUM(1.0)")
            );
        }

        @TestFactory
        @DisplayName("'+' is dropped in prefix position and addition otherwise")
        Stream<DynamicTest> plus() {
            return lexes(
                    row("+1", "NUM(1.0)"),
                    row("++1", "NUM(1.0)"),
                    row("1 + +1", "NUM(1.0) PLUS NUM(1.0)"),
                    row("(+1)", "LEFT_PAREN NUM(1.0) RIGHT_PAREN"),
                    row("f(1, +1)", "ID(f) LEFT_PAREN NUM(1.0) COMMA NUM(1.0) RIGHT_PAREN"),
                    row("-+1", "NEG NUM(1.0)"),
                    row("x+1", "ID(x) PLUS NUM(1.0)"),
                    row("(x)+1", "LEFT_PAREN ID(x) RIGHT_PAREN PLUS NUM(1.0)")
            );
        }

        @TestFactory
        @DisplayName("'!' is always NOT unless followed by '='")
        Stream<DynamicTest> not() {
            return lexes(
                    row("!x", "NOT ID(x)"),
                    row("x && !y", "ID(x) CONDITIONAL_AND NOT ID(y)"),
                    row("f(!x)", "ID(f) LEFT_PAREN NOT ID(x) RIGHT_PAREN"),
                    row("x != y", "ID(x) NOT_EQUAL ID(y)")
            );
        }
    }

    @Nested
    @DisplayName("Numbers")
    class Numbers {

        @TestFactory
        @DisplayName("Numeric literals are parsed as Double")
        Stream<DynamicTest> numbers() {
            return lexes(
                    row("0", "NUM(0.0)"),
                    row("42", "NUM(42.0)"),
                    row("007", "NUM(7.0)"),
                    row("3.14", "NUM(3.14)"),
                    row("3.50", "NUM(3.5)"),
                    row("1000000", "NUM(1000000.0)"),
                    // a number followed directly by letters is two tokens
                    row("1abc", "NUM(1.0) ID(abc)")
            );
        }

        @Test
        @DisplayName("Literal is a Double and lexeme is the source text")
        void literalType() {
            var token = lex("3.50").getFirst();
            assertEquals(TokenType.NUMBER, token.type());
            assertEquals(Double.class, token.literal().getClass());
            assertEquals(3.5, token.literal());
            assertEquals("3.50", token.lexeme());
        }

        @TestFactory
        @DisplayName("Malformed numbers")
        Stream<DynamicTest> malformed() {
            return fails(
                    row("1.", "Unexpected character '.'"),
                    row(".5", "Unexpected character '.'"),
                    row("1.2.3", "Unexpected character '.'")
            );
        }
    }

    @Nested
    @DisplayName("Strings")
    class Strings {

        @TestFactory
        @DisplayName("String literals")
        Stream<DynamicTest> strings() {
            return lexes(
                    row("'abc'", "STR(abc)"),
                    row("\"abc\"", "STR(abc)"),
                    row("''", "STR()"),
                    row("\"\"", "STR()"),
                    // whitespace and operators inside a string are preserved
                    row("'a  b'", "STR(a  b)"),
                    row("'1 + 2'", "STR(1 + 2)"),
                    row("'// not a comment'", "STR(// not a comment)"),
                    // the other quote character is an ordinary character
                    row("'say \"hi\"'", "STR(say \"hi\")"),
                    row("\"it's\"", "STR(it's)"),
                    // strings may span lines
                    row("'a\nb'", "STR(a\\nb)"),
                    row("'a' + \"b\"", "STR(a) PLUS STR(b)")
            );
        }

        @Test
        @DisplayName("Lexeme includes the quotes, literal does not")
        void lexemeAndLiteral() {
            var token = lex("'abc'").getFirst();
            assertEquals(TokenType.STRING, token.type());
            assertEquals("'abc'", token.lexeme());
            assertEquals("abc", token.literal());
        }

        @TestFactory
        @DisplayName("Unterminated strings")
        Stream<DynamicTest> unterminated() {
            return fails(
                    row("'abc", "Unterminated string"),
                    row("\"abc", "Unterminated string"),
                    row("'abc\"", "Unterminated string"),
                    row("'", "Unterminated string"),
                    row("x + 'a\nb", "Unterminated string")
            );
        }

        @Test
        @DisplayName("Member access on a string is rejected")
        void dotAfterString() {
            assertLexFails("'abc'.length", "Unexpected character '.'");
        }
    }

    @Nested
    @DisplayName("Identifiers and keywords")
    class Identifiers {

        @TestFactory
        @DisplayName("Identifiers")
        Stream<DynamicTest> identifiers() {
            return lexes(
                    row("x", "ID(x)"),
                    row("_x", "ID(_x)"),
                    row("x1", "ID(x1)"),
                    row("x_y_2", "ID(x_y_2)"),
                    row("CamelCase", "ID(CamelCase)"),
                    // dotted names form a single namespaced identifier
                    row("dim.id", "ID(dim.id)"),
                    row("a.b.c", "ID(a.b.c)"),
                    row("lib.oneOf", "ID(lib.oneOf)"),
                    row("a._b", "ID(a._b)"),
                    row("a.b1", "ID(a.b1)"),
                    row("lib.iif(x, 1, 2)", "ID(lib.iif) LEFT_PAREN ID(x) COMMA NUM(1.0) COMMA NUM(2.0) RIGHT_PAREN")
            );
        }

        @TestFactory
        @DisplayName("Keywords are case-insensitive")
        Stream<DynamicTest> keywords() {
            return lexes(
                    row("true", "TRUE"),
                    row("TRUE", "TRUE"),
                    row("True", "TRUE"),
                    row("false", "FALSE"),
                    row("FALSE", "FALSE"),
                    row("fAlSe", "FALSE"),
                    // only exact keyword matches count
                    row("trueish", "ID(trueish)"),
                    row("isTrue", "ID(isTrue)"),
                    row("true.x", "ID(true.x)"),
                    row("true && false", "TRUE CONDITIONAL_AND FALSE")
            );
        }

        @TestFactory
        @DisplayName("Malformed dotted names")
        Stream<DynamicTest> malformed() {
            return fails(
                    row("a.", "Unexpected end of line"),
                    row("a. b", "Unexpected character ' '"),
                    row("a.1", "Unexpected character '1'"),
                    row("a..b", "Unexpected character '.'"),
                    row("a.(", "Unexpected character '('")
            );
        }
    }

    @Nested
    @DisplayName("Whitespace and comments")
    class WhitespaceAndComments {

        @TestFactory
        @DisplayName("Whitespace is ignored")
        Stream<DynamicTest> whitespace() {
            return lexes(
                    row("  x  ", "ID(x)"),
                    row("\tx\t+\t1", "ID(x) PLUS NUM(1.0)"),
                    row("x\n+\n1", "ID(x) PLUS NUM(1.0)"),
                    row("x\r\n+\r\n1", "ID(x) PLUS NUM(1.0)")
            );
        }

        @TestFactory
        @DisplayName("Comments run to the end of the line")
        Stream<DynamicTest> comments() {
            return lexes(
                    row("x // comment", "ID(x)"),
                    row("x // comment\n+ 1", "ID(x) PLUS NUM(1.0)"),
                    row("// first\nx\n// last", "ID(x)"),
                    row("x // a // b", "ID(x)"),
                    row("x //", "ID(x)"),
                    row("x / / y", "ID(x) SLASH SLASH ID(y)")
            );
        }
    }

    @Nested
    @DisplayName("Invalid characters")
    class InvalidCharacters {

        @TestFactory
        @DisplayName("Rejected characters")
        Stream<DynamicTest> rejected() {
            return fails(
                    row("=", "Unexpected character '='"),
                    row("a = b", "Unexpected character '='"),
                    row("|", "Unexpected character '|'"),
                    row("a | b", "Unexpected character '|'"),
                    row("&", "Unexpected character '&'"),
                    row("a & b", "Unexpected character '&'"),
                    row(".", "Unexpected character '.'"),
                    row("#", "Unexpected character '#'"),
                    row("x @ y", "Unexpected character '@'"),
                    row("$x", "Unexpected character '$'"),
                    row("x % 2", "Unexpected character '%'"),
                    row("[1]", "Unexpected character '['"),
                    row("x;", "Unexpected character ';'")
            );
        }

        @TestFactory
        @DisplayName("Single '=', '|' and '&' suggest the two-character operator")
        Stream<DynamicTest> suggestions() {
            return fails(
                    row("a = b", "did you mean '=='?"),
                    row("a | b", "did you mean '||'?"),
                    row("a & b", "did you mean '&&'?")
            );
        }
    }

    @Nested
    @DisplayName("Source locations")
    class Locations {

        @TestFactory
        @DisplayName("Tokens record line, column and absolute position")
        Stream<DynamicTest> tokenLocations() {
            return Stream.of(
                    at("x + 10", 0, "x", 1, 0, 0),
                    at("x + 10", 1, "+", 1, 2, 2),
                    at("x + 10", 2, "10", 1, 4, 4),
                    at("x + 10", 3, "", 1, 6, 6),                      // EOF
                    at("a\n  b\n\tc", 1, "b", 2, 2, 4),
                    at("a\n  b\n\tc", 2, "c", 3, 1, 7),
                    at("a\r\nb", 1, "b", 2, 0, 3),
                    at("x // c\ny", 1, "y", 2, 0, 7),
                    at("f(a, b)", 4, "b", 1, 5, 5),
                    at("x <= y", 1, "<=", 1, 2, 2),
                    // a multi-line string is located at its opening quote...
                    at("'x\ny' z", 0, "'x\ny'", 1, 0, 0),
                    // ...and tokens after it are on the line where it ended
                    at("'x\ny' z", 1, "z", 2, 3, 6)
            ).map(loc -> dynamicTest("[%s] token %d '%s' at (%d, %d) offset %d".formatted(
                    printable(loc.script()), loc.index(), printable(loc.lexeme()), loc.line(), loc.column(), loc.position()), () -> {
                var token = lex(loc.script()).get(loc.index());
                assertEquals(loc.lexeme(), token.lexeme(), "lexeme");
                assertEquals(loc.line(), token.line(), "line");
                assertEquals(loc.column(), token.column(), "column");
                assertEquals(loc.position(), token.position(), "position");
            }));
        }

        @TestFactory
        @DisplayName("Errors report line and 1-based column")
        Stream<DynamicTest> errorLocations() {
            return Stream.of(
                    row("#", "(1, 1)"),
                    row("x + #", "(1, 5)"),
                    row("a\n  #", "(2, 3)"),
                    row("true &&\n  true &&\n    $", "(3, 5)"),
                    row("a = b", "(1, 3)"),
                    // unterminated strings point at the opening quote
                    row("x + 'abc", "(1, 5)"),
                    row("x\n + 'a\nb", "(2, 4)"),
                    // malformed dotted names point at the offending character
                    row("a.1", "(1, 3)"),
                    row("a.", "(1, 3)")
            ).map(c -> dynamicTest("[%s]  reports  %s".formatted(printable(c[0]), c[1]), () -> {
                var ex = assertThrows(ScriptException.class, () -> lex(c[0]));
                assertTrue(ex.getMessage().startsWith(c[1]),
                        () -> "Message [" + ex.getMessage() + "] does not start with [" + c[1] + "]");
            }));
        }
    }
}