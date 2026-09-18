package org.orecruncher.dsurround.lib.scripting.engine;

import org.jetbrains.annotations.NotNull;

record Token(TokenType type, String lexeme, Object literal, int line, int position) {

    @Override
    public @NotNull String toString() {
        return "%s %s %s".formatted(this.type, this.lexeme, this.literal);
    }

    public static Token from(TokenType type, String lexeme, Object literal, int line, int position) {
        return new Token(type, lexeme, literal, line, position);
    }
}