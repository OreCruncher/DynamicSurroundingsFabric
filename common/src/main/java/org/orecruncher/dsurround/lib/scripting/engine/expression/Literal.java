package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.engine.Token;
import org.orecruncher.dsurround.lib.scripting.engine.TokenType;

public record Literal(Token token, Object value) implements Expression {

    public static Literal from(Token token) {
        var value = switch (token.type()) {
            case TokenType.TRUE -> Boolean.TRUE;
            case TokenType.FALSE -> Boolean.FALSE;
            default -> token.literal();
        };
        return new Literal(token, value);
    }

    @Override
    public Object eval() {
        return this.value;
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("value", this.token.lexeme())
                .add("type", this.token.type())
                .toString();
    }
}
