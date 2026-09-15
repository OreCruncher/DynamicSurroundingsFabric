package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.orecruncher.dsurround.lib.collections.Triplet;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ParseTokenTests {

    private static final List<Triplet<String, List<Token>, List<RpnConverter.RpnToken>>> PARSE_TEST_DATA = ImmutableList.of(
            Triplet.of(
                    "1",
                    ImmutableList.of(
                        Token.from(TokenType.NUMBER, "1", 1.0, 1, 0),
                        Token.from(TokenType.EOF, "", null, 1, 0)
                    ),
                    ImmutableList.of(
                            RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "1", 1.0, 1, 0)))
            ),
            Triplet.of(
                    "-1",
                    ImmutableList.of(
                        Token.from(TokenType.NEG, "-", null, 1, 0),
                        Token.from(TokenType.NUMBER, "1", 1.0, 1, 1),
                        Token.from(TokenType.EOF, "", null, 1, 1)),
                    ImmutableList.of(
                            RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "1", 1.0, 1, 1)),
                            RpnConverter.RpnToken.of(Token.from(TokenType.NEG, "-", null, 1, 0)))

            ),
            Triplet.of(
                    "1 + 1",
                    ImmutableList.of(
                        Token.from(TokenType.NUMBER, "1", 1.0, 1, 0),
                        Token.from(TokenType.PLUS, "+", null, 1, 2),
                        Token.from(TokenType.NUMBER, "1", 1.0, 1, 4),
                        Token.from(TokenType.EOF, "", null, 1, 4)),
                    ImmutableList.of(
                        RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "1", 1.0, 1, 0)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "1", 1.0, 1, 4)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.PLUS, "+", null, 1, 2)))

            ),
            Triplet.of(
                    "math.pi",
                    ImmutableList.of(
                        Token.from(TokenType.IDENTIFIER, "math.pi", null, 1, 0),
                        Token.from(TokenType.EOF, "", null, 1, 0)),
                    ImmutableList.of(
                        RpnConverter.RpnToken.of(Token.from(TokenType.IDENTIFIER, "math.pi", null, 1, 0)))
            ),
            Triplet.of(
                    "math.pow(2, 2)",
                    ImmutableList.of(
                        Token.from(TokenType.IDENTIFIER, "math.pow", null, 1, 0),
                        Token.from(TokenType.LEFT_PAREN, "(", null, 1, 8),
                        Token.from(TokenType.NUMBER, "2", 2.0, 1, 9),
                        Token.from(TokenType.COMMA, ",", null, 1, 10),
                        Token.from(TokenType.NUMBER, "2", 2.0, 1, 12),
                        Token.from(TokenType.RIGHT_PAREN, ")", null, 1, 13),
                        Token.from(TokenType.EOF, "", null, 1, 13)),
                    ImmutableList.of(
                        RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "2", 2.0, 1, 9)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "2", 2.0, 1, 12)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.IDENTIFIER, "math.pow", null, 1, 0), 2))

            ),
            Triplet.of(
                    "math.cos(math.toRadians(45)) == math.sqrt(2)/2",
                    ImmutableList.of(
                        Token.from(TokenType.IDENTIFIER, "math.cos", null, 1, 0),
                        Token.from(TokenType.LEFT_PAREN, "(", null, 1, 8),
                        Token.from(TokenType.IDENTIFIER, "math.toRadians", null, 1, 9),
                        Token.from(TokenType.LEFT_PAREN, "(", null, 1, 23),
                        Token.from(TokenType.NUMBER, "45", 45.0, 1, 24),
                        Token.from(TokenType.RIGHT_PAREN, ")", null, 1, 26),
                        Token.from(TokenType.RIGHT_PAREN, ")", null, 1, 27),
                        Token.from(TokenType.EQUAL_EQUAL, "==", null, 1, 29),
                        Token.from(TokenType.IDENTIFIER, "math.sqrt", null, 1, 32),
                        Token.from(TokenType.LEFT_PAREN, "(", null, 1, 41),
                        Token.from(TokenType.NUMBER, "2", 2.0, 1, 42),
                        Token.from(TokenType.RIGHT_PAREN, ")", null, 1, 43),
                        Token.from(TokenType.SLASH, "/", null, 1, 44),
                        Token.from(TokenType.NUMBER, "2", 2.0, 1, 45),
                        Token.from(TokenType.EOF, "", null, 1, 45)),
                    ImmutableList.of(
                        RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "45", 45.0, 1, 24)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.IDENTIFIER, "math.toRadians", null, 1, 9), 1),
                        RpnConverter.RpnToken.of(Token.from(TokenType.IDENTIFIER, "math.cos", null, 1, 0), 1),
                        RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "2", 2.0, 1, 42)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.IDENTIFIER, "math.sqrt", null, 1, 32), 1),
                        RpnConverter.RpnToken.of(Token.from(TokenType.NUMBER, "2", 2.0, 1, 45)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.SLASH, "/", null, 1, 44)),
                        RpnConverter.RpnToken.of(Token.from(TokenType.EQUAL_EQUAL, "==", null, 1, 29)))
            )
    );

    @TestFactory
    public Stream<DynamicTest> parseDynamicTests() {
        var engine = new ScriptEngine();
        return PARSE_TEST_DATA.stream()
                .map(data -> DynamicTest.dynamicTest("Parse \"%s\"".formatted(data.first()), () -> {
                    var lexer = new Lexer(data.first());
                    var tokens = lexer.getTokens();
                    assertNotNull(tokens);
                    assertEquals(tokens.size(), data.second().size());
                    //var text = generateLexerTokenString(tokens);
                    assertIterableEquals(tokens, data.second());

                    var rpn = new RpnConverter(engine.environment);
                    var rpnTokens = rpn.infixToRpn(tokens);
                    assertNotNull(rpnTokens);
                    assertEquals(rpnTokens.size(), data.third().size());
                    //var text = generateRpnTokenString(rpnTokens);
                    assertIterableEquals(rpnTokens, data.third());
                }));
    }

    private static String generateLexerTokenString(List<Token> tokens) {
        var builder = new StringBuilder();
        boolean doneFirstLine = false;
        for (var token : tokens) {
            if (doneFirstLine)
                builder.append(",\n");
            var tokenString = "Token.from(TokenType.%s, \"%s\", %s, %d, %d)".formatted(token.type(), token.lexeme(), token.literal(), token.line(), token.position());
            builder.append(tokenString);
            doneFirstLine = true;
        }
        return builder.toString();
    }

    private static String generateRpnTokenString(List<RpnConverter.RpnToken> tokens) {
        var builder = new StringBuilder();
        boolean doneFirstLine = false;
        for (var token : tokens) {
            if (doneFirstLine)
                builder.append(",\n");
            var t = token.token();
            var tokenString = "Token.from(TokenType.%s, \"%s\", %s, %d, %d)".formatted(t.type(), t.lexeme(), t.literal(), t.line(), t.position());
            String rpnTokenString;
            if (token.isFunction()) {
                rpnTokenString = "RpnConverter.RpnToken.of(%s, %d)".formatted(tokenString, token.argCount());
            } else {
                rpnTokenString = "RpnConverter.RpnToken.of(%s)".formatted(tokenString);
            }
            builder.append(rpnTokenString);

            doneFirstLine = true;
        }
        return builder.toString();
    }

}
