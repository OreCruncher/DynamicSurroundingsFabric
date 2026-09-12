package org.orecruncher.dsurround.lib.scripting.engine;

enum TokenType {
    // Punctuation
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    DOT,

    // Binary operators
    MINUS(7, false, false, true),
    PLUS(7, false, false, true),
    SLASH(8, false, false, true),
    STAR(8, false, false, true),
    NOT_EQUAL(5, false, false, true),
    EQUAL_EQUAL(5, false, false, true),
    GREATER(6, false, false, true),
    GREATER_EQUAL(6, false, false, true),
    LESS(6, false, false, true),
    LESS_EQUAL(6, false, false, true),
    CONDITIONAL_OR(1, false, false, true),
    CONDITIONAL_AND(2, false, false, true),

    // Unary operators
    NOT(9, true, true, false),

    // Literals
    IDENTIFIER,
    STRING,
    NUMBER,

    // Keywords
    FALSE,
    TRUE,

    // Special
    EOF;

    private final int precedence;
    private final boolean rightAssociative;
    private final boolean isUnary;
    private final boolean isBinary;

    TokenType() {
        this(-1, false, false, false);
    }

    TokenType(final int precedence, boolean rightAssociative, boolean isUnary, boolean isBinary) {
        this.precedence = precedence;
        this.rightAssociative = rightAssociative;
        this.isUnary = isUnary;
        this.isBinary = isBinary;
    }

    public int getPrecedence() {
        return this.precedence;
    }

    public boolean isRightAssociative() {
        return this.rightAssociative;
    }

    public boolean isUnary() {
        return this.isUnary;
    }

    public boolean isBinary() {
        return this.isBinary;
    }

    public boolean isOperator() {
        return this.isUnary || this.isBinary;
    }

    public static final int NO_PRECEDENCE = -1;
}