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
            case NOT -> o -> !toBoolean(operator, o.eval());
            case NEG -> o -> -toDouble(operator, o.eval());
            default -> {
                ScriptException.throwException(operator, "Unknown unary operator type '%s'".formatted(operator.lexeme()));
                yield null;
            }
        };
    }

    // Conversions do not use exceptions internally: a failure throws exactly one ScriptException, with the
    // operator's location. Numbers and booleans take a fast path that avoids boxing.

    private static double toDouble(Token operator, Object value) {
        if (value instanceof Number n)
            return n.doubleValue();
        var converted = ScriptHelpers.tryToDouble(value);
        if (converted != null)
            return converted;
        ScriptException.throwException(operator, "Operand must be a number or value that converts to a number");
        return 0D;
    }

    private static boolean toBoolean(Token operator, Object value) {
        if (value instanceof Boolean b)
            return b;
        var converted = ScriptHelpers.tryToBoolean(value);
        if (converted != null)
            return converted;
        ScriptException.throwException(operator, "Operand must be a boolean or value that converts to a boolean");
        return false;
    }

    @FunctionalInterface
    interface IUnaryOperationHandler {
        Object eval(Expression expr1);
    }
}
