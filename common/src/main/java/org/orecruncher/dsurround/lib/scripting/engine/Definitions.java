package org.orecruncher.dsurround.lib.scripting.engine;

import java.util.*;

import static org.orecruncher.dsurround.lib.scripting.engine.TokenType.FALSE;
import static org.orecruncher.dsurround.lib.scripting.engine.TokenType.TRUE;

final class Definitions {
    static final Map<String, TokenType> KEYWORDS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static final EnumMap<TokenType, Integer> PRECEDENCE = new EnumMap<>(TokenType.class);
    static final Set<TokenType> RIGHT_ASSOCIATIVE = EnumSet.noneOf(TokenType.class);
    static final Set<TokenType> BINARY_OPERATORS = EnumSet.noneOf(TokenType.class);
    static final Set<TokenType> UNARY_OPERATORS = EnumSet.noneOf(TokenType.class);

    static {
        KEYWORDS.put("false", FALSE);
        KEYWORDS.put("true", TRUE);

        PRECEDENCE.put(TokenType.CONDITIONAL_OR, 1);
        PRECEDENCE.put(TokenType.CONDITIONAL_AND, 2);
        PRECEDENCE.put(TokenType.EQUAL_EQUAL, 5);
        PRECEDENCE.put(TokenType.NOT_EQUAL, 5);
        PRECEDENCE.put(TokenType.GREATER, 6);
        PRECEDENCE.put(TokenType.GREATER_EQUAL, 6);
        PRECEDENCE.put(TokenType.LESS, 6);
        PRECEDENCE.put(TokenType.LESS_EQUAL, 6);
        PRECEDENCE.put(TokenType.PLUS, 7);
        PRECEDENCE.put(TokenType.MINUS, 7);
        PRECEDENCE.put(TokenType.STAR, 8);
        PRECEDENCE.put(TokenType.SLASH, 8);
        PRECEDENCE.put(TokenType.NOT,9);

        RIGHT_ASSOCIATIVE.add(TokenType.NOT);

        UNARY_OPERATORS.add(TokenType.NOT);

        BINARY_OPERATORS.addAll(
                List.of(TokenType.CONDITIONAL_OR,
                        TokenType.CONDITIONAL_AND,
                        TokenType.EQUAL_EQUAL,
                        TokenType.NOT_EQUAL,
                        TokenType.GREATER,
                        TokenType.GREATER_EQUAL,
                        TokenType.LESS,
                        TokenType.LESS_EQUAL,
                        TokenType.PLUS,
                        TokenType.MINUS,
                        TokenType.STAR,
                        TokenType.SLASH
                ));
    }

    boolean isKeyword(Token token) {
        return KEYWORDS.containsKey(token.lexeme());
    }

    static boolean isBinaryOperator(Token token) {
        return BINARY_OPERATORS.contains(token.type());
    }

    static boolean isUnaryOperator(Token token) {
        return UNARY_OPERATORS.contains(token.type());
    }

    static boolean isOperator(Token token) {
        return isBinaryOperator(token) || isUnaryOperator(token);
    }

}
