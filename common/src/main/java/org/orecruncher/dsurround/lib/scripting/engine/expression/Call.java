package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.IScriptFunction;
import org.orecruncher.dsurround.lib.scripting.engine.Environment;
import org.orecruncher.dsurround.lib.scripting.engine.Token;

import java.util.List;

public record Call(Token token, Expression[] arguments, Object[] values, IScriptFunction function) implements Expression {

    private final static Expression[] NO_ARGUMENTS = {};
    private final static Object[] NO_VALUES = {};

    public static Call from(Environment environment, Token token, List<Expression> arguments) {
        var functionHandler = environment.getFunctionHandler(token);
        var args = arguments.isEmpty() ? NO_ARGUMENTS : arguments.toArray(new Expression[0]);
        var values = arguments.isEmpty() ? NO_VALUES : new Object[arguments.size()];
        return new Call(token, args, values, functionHandler);
    }

    @Override
    public Object eval() {
        for (int i = 0; i < this.arguments.length; i++) {
            this.values[i] = this.arguments[i].eval();
        }
        return this.function.evaluate(this.values);
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.token.lexeme())
                .add("args", this.arguments.length)
                .toString();
    }
}
