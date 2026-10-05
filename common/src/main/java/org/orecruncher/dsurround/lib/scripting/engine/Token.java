package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;

/**
 * A lexical token.
 *
 * @param type     Token type
 * @param lexeme   Source text of the token
 * @param literal  Literal value, if any
 * @param line     1-based line number where the token starts
 * @param column   0-based column within {@code line} where the token starts
 * @param position 0-based absolute offset into the script where the token starts
 */
public record Token(TokenType type, String lexeme, Object literal, int line, int column, int position) {

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", this.type)
                .add("lexeme", this.lexeme)
                .add("literal", this.literal)
                .toString();
    }

    public static Token from(TokenType type, String lexeme, Object literal, int line, int column, int position) {
        return new Token(type, lexeme, literal, line, column, position);
    }

    /**
     * Creates a new token that reports the same source location as {@code at}. Used by the compiler when
     * synthesizing tokens during constant folding.
     */
    public static Token derive(TokenType type, String lexeme, Object literal, Token at) {
        return new Token(type, lexeme, literal, at.line(), at.column(), at.position());
    }
}
