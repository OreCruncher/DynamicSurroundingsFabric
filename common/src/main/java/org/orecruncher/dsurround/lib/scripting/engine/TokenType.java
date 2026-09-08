package org.orecruncher.dsurround.lib.scripting.engine;

enum TokenType {
    // Single-character tokens
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    DOT,
    MINUS,
    PLUS,
    SEMICOLON,
    SLASH,
    STAR,

    // One or two character tokens
    NOT,
    NOT_EQUAL,
    EQUAL,          // TODO: Revisit since language does not really need
    EQUAL_EQUAL,
    GREATER,
    GREATER_EQUAL,
    LESS,
    LESS_EQUAL,
    CONDITIONAL_OR,
    CONDITIONAL_AND,

    // Literals
    IDENTIFIER,
    STRING,
    NUMBER,

    // Keywords
    FALSE,
    TRUE,

    // Special
    EOF
}