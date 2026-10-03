package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;

/**
 * Compiled form of lib.iif(condition, whenTrue, whenFalse). Only the selected branch is evaluated, and nothing is
 * allocated per evaluation.
 */
public final class Conditional implements Expression {

    private final Call call;
    private final Expression condition;
    private final Expression whenTrue;
    private final Expression whenFalse;

    private Conditional(Call call) {
        this.call = call;
        this.condition = call.argument(0);
        this.whenTrue = call.argument(1);
        this.whenFalse = call.argument(2);
    }

    /**
     * Compiles a lib.iif call. A constant condition selects the branch now.
     */
    public static Expression compile(Call call) {
        if (call.isConstant(0))
            return (Boolean) call.constant(0) ? call.argument(1) : call.argument(2);
        return new Conditional(call);
    }

    @Override
    public Object eval() {
        var value = this.condition.eval();
        // Converted the same way as any other boolean argument, with errors reported at the condition
        var flag = value instanceof Boolean b ? b : (Boolean) this.call.convert(0, value);
        return flag ? this.whenTrue.eval() : this.whenFalse.eval();
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.call.token().lexeme())
                .toString();
    }
}
