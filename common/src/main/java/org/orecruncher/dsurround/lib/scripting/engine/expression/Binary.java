package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.lib.scripting.engine.Token;

public record Binary(Expression left, Token operator, Expression right, IBinaryOperationHandler function) implements Expression {

    public static Binary from(Expression left, Token operator, Expression right) {
        var function = getFunction(operator);
        return new Binary(left, operator, right, function);
    }

    private static IBinaryOperationHandler getFunction(Token operator) {
        return switch (operator.type()) {
            case NOT_EQUAL -> (l, r) -> !isEqual(l.eval(), r.eval());
            case EQUAL_EQUAL -> (l, r) -> isEqual(l.eval(), r.eval());
            case GREATER -> (l, r) -> toDouble(operator, l.eval(), true) > toDouble(operator, r.eval(), false);
            case GREATER_EQUAL -> (l, r) -> toDouble(operator, l.eval(), true) >= toDouble(operator, r.eval(), false);
            case LESS -> (l, r) -> toDouble(operator, l.eval(), true) < toDouble(operator, r.eval(), false);
            case LESS_EQUAL -> (l, r) -> toDouble(operator, l.eval(), true) <= toDouble(operator, r.eval(), false);
            case MINUS -> (l, r) -> toDouble(operator, l.eval(), true) - toDouble(operator, r.eval(), false);
            case PLUS -> (l, r) -> {
                var leftValue = l.eval();
                var rightValue = r.eval();
                if (leftValue instanceof Number n1 && rightValue instanceof Number n2) {
                    return n1.doubleValue() + n2.doubleValue();
                }

                if (leftValue instanceof String || rightValue instanceof String) {
                    return leftValue.toString() + rightValue.toString();
                }
                ScriptException.throwException(operator, "Incompatible operands for operator '%s'".formatted(operator.lexeme()));
                return null;
            };
            case SLASH -> (l, r) -> toDouble(operator, l.eval(), true) / toDouble(operator, r.eval(), false);
            case STAR -> (l, r) -> toDouble(operator, l.eval(), true) * toDouble(operator, r.eval(), false);
            case CONDITIONAL_OR -> (l, r) -> toBoolean(operator, l.eval(), true) || toBoolean(operator, r.eval(), false);
            case CONDITIONAL_AND -> (l, r) -> toBoolean(operator, l.eval(), true) && toBoolean(operator, r.eval(), false);
            default -> {
                ScriptException.throwException(operator, "Unknown binary operator type '%s'".formatted(operator.lexeme()));
                yield null;
            }
        };
    }

    @Override
    public Object eval() {
        // Defer evaluation of operands. Depending on the operand both may not need to be
        // evaluated (such as && or lib.oneOf).
        return this.function.eval(this.left, this.right);
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("operator", this.operator.lexeme())
                .toString();
    }

    private static boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;
        return a.equals(b);
    }

    private static double toDouble(Token operator, Object value, boolean leftOperand) {
        try {
            return ScriptHelpers.toDouble(value);
        } catch (Throwable ignored) {
        }
        ScriptException.throwException(operator, "%s operand must be a number or value that converts to a number".formatted(leftOperand ? "Left" : "Right"));
        return 0D;
    }

    private static boolean toBoolean(Token operator, Object value, boolean leftOperand) {
        try {
            return ScriptHelpers.toBoolean(value);
        } catch (Throwable ignored) {
        }
        ScriptException.throwException(operator, "%s operand must be a boolean or value that converts to a boolean".formatted(leftOperand ? "Left" : "Right"));
        return false;
    }

    @FunctionalInterface
    interface IBinaryOperationHandler {
        Object eval(Expression expr1, Expression expr2);
    }
}
