package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;

public record Token(TokenType type, String lexeme, Object literal, int line, int position) {

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", this.type)
                .add("lexeme", this.lexeme)
                .add("literal", this.literal)
                .toString();
    }

    public static Token from(TokenType type, String lexeme, Object literal, int line, int position) {
        return new Token(type, lexeme, literal, line, position);
    }
}