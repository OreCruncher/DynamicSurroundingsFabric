package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.lib.scripting.engine.Token;

public record Unary(Token operator, Expression right, IUnaryOperationHandler function) implements Expression {

    public static Unary from(Token operator, Expression right) {
        var function = getFunction(operator);
        return new Unary(operator, right, function);
    }

    @Override
    public Object eval() {
        return this.function.eval(this.right);
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("operator", this.operator.lexeme())
                .toString();
    }

    private static IUnaryOperationHandler getFunction(Token operator) {
        return switch (operator.type()) {
            case NOT -> o -> !ScriptHelpers.toBoolean(o.eval());
            case NEG -> o -> -ScriptHelpers.toDouble(o.eval());
            default -> {
                ScriptException.throwException(operator, "Unknown unary operator type '%s'".formatted(operator.lexeme()));
                yield null;
            }
        };
    }

    @FunctionalInterface
    interface IUnaryOperationHandler {
        Object eval(Expression expr1);
    }
}
