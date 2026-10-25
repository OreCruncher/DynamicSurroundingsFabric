package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.lib.scripting.engine.Token;

/**
 * Compiled form of the conditional operator (condition ? whenTrue : whenFalse) and of lib.iif(condition, whenTrue,
 * whenFalse). Only the selected branch is evaluated, and nothing is allocated per evaluation.
 */
public final class Conditional implements Expression {

    private final Token token;
    private final Expression condition;
    private final Token conditionStart;
    private final String error;
    private final Expression whenTrue;
    private final Expression whenFalse;

    private Conditional(Token token, Expression condition, Token conditionStart, String error, Expression whenTrue, Expression whenFalse) {
        this.token = token;
        this.condition = condition;
        this.conditionStart = conditionStart;
        this.error = error;
        this.whenTrue = whenTrue;
        this.whenFalse = whenFalse;
    }

    /**
     * Compiles the conditional operator. A constant condition selects the branch now.
     *
     * @param token          The '?', for the node's name
     * @param condition      The condition
     * @param conditionStart Where the condition starts in the script; a condition that is not a boolean is
     *                       reported there
     */
    public static Expression compile(Token token, Expression condition, Token conditionStart, Expression whenTrue, Expression whenFalse) {
        var error = "Condition must be a boolean or value that converts to a boolean";
        if (condition instanceof Literal literal)
            return toBoolean(literal.value(), conditionStart, error) ? whenTrue : whenFalse;
        return new Conditional(token, condition, conditionStart, error, whenTrue, whenFalse);
    }

    /**
     * Compiles a lib.iif call. A constant condition was already converted, and selects the branch now.
     */
    public static Expression compile(Call call) {
        if (call.isConstant(0))
            return (Boolean) call.constant(0) ? call.argument(1) : call.argument(2);
        var error = "%s: argument 1 must be a boolean".formatted(call.function().name());
        return new Conditional(call.token(), call.argument(0), call.argumentToken(0), error, call.argument(1), call.argument(2));
    }

    @Override
    public Object eval() {
        return toBoolean(this.condition.eval(), this.conditionStart, this.error) ? this.whenTrue.eval() : this.whenFalse.eval();
    }

    public Expression condition() {
        return this.condition;
    }

    public Expression whenTrue() {
        return this.whenTrue;
    }

    public Expression whenFalse() {
        return this.whenFalse;
    }

    private static boolean toBoolean(Object value, Token at, String error) {
        if (value instanceof Boolean b)
            return b;
        var converted = ScriptHelpers.tryToBoolean(value);
        if (converted != null)
            return converted;
        ScriptException.throwException(at, error);
        return false;
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.token.lexeme())
                .toString();
    }
}
