package org.orecruncher.dsurround.lib.scripting.engine;

public enum TokenType {
    // Punctuation
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,

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
    NEG(9, true, true, false),

    // Conditional operator (a ? b : c). Lower precedence than every other operator, and right associative so that
    // a ? b : c ? d : e reads as a ? b : (c ? d : e). The converter swaps a matched QUESTION for a TERNARY, which
    // the compiler sees as an operator taking three operands.
    QUESTION(0, true, false, false, true),
    COLON(0, true, false, false, true),
    TERNARY(0, true, false, false, true),

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
    private final boolean isTernary;

    /**
     * Default CTOR that is not an operator of some sort.
     */
    TokenType() {
        this(-1, false, false, false);
    }

    /**
     * CTOR for tokens that are an operator
     * @param precedence Precedence of the operator as compared to its peers. Lower number mean less precedence.
     * @param rightAssociative The operator is right associative (operates on the operand to the right)
     * @param isUnary The operator takes a single operand.
     * @param isBinary The operator takes two operands.
     */
    TokenType(final int precedence, boolean rightAssociative, boolean isUnary, boolean isBinary) {
        this(precedence, rightAssociative, isUnary, isBinary, false);
    }

    /**
     * CTOR for tokens that are an operator
     * @param precedence Precedence of the operator as compared to its peers. Lower number mean less precedence.
     * @param rightAssociative The operator is right associative (operates on the operand to the right)
     * @param isUnary The operator takes a single operand.
     * @param isBinary The operator takes two operands.
     * @param isTernary The operator is part of the conditional operator, which takes three operands.
     */
    TokenType(final int precedence, boolean rightAssociative, boolean isUnary, boolean isBinary, boolean isTernary) {
        this.precedence = precedence;
        this.rightAssociative = rightAssociative;
        this.isUnary = isUnary;
        this.isBinary = isBinary;
        this.isTernary = isTernary;
    }

    public int getPrecedence() {
        return this.precedence;
    }

    public boolean isRightAssociative() {
        return this.rightAssociative;
    }

    public boolean isUnaryOperator() {
        return this.isUnary;
    }

    public boolean isBinaryOperator() {
        return this.isBinary;
    }

    public boolean isTernaryOperator() {
        return this.isTernary;
    }

    public boolean isOperator() {
        return this.isUnary || this.isBinary || this.isTernary;
    }

    public static final int NO_PRECEDENCE = -1;

    public static boolean isLiteral(TokenType type) {
        return type == TokenType.NUMBER || type == TokenType.STRING || type == TokenType.TRUE || type == TokenType.FALSE;
    }

    public static boolean isIdentifier(TokenType type) {
        return type == TokenType.IDENTIFIER;
    }
}