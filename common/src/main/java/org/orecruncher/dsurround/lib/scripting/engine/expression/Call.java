package org.orecruncher.dsurround.lib.scripting.engine.expression;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.ScriptArguments;
import org.orecruncher.dsurround.lib.scripting.ScriptFunction;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.Token;

/**
 * A call to a script function. Arguments are converted to the declared parameter types before the handler runs;
 * constant arguments were already converted when the script was compiled.
 */
public final class Call implements Expression {

    private static final ScriptArguments NO_ARGUMENTS = new Values(new Object[0]);

    private final Token token;
    private final ScriptFunction function;
    private final Expression[] arguments;
    private final Token[] argumentTokens;
    private final Object[] constants;
    private final boolean[] isConstant;

    /**
     * @param token          The function name token
     * @param function       The function being called
     * @param arguments      Argument expressions
     * @param argumentTokens Where each argument starts in the script, for error locations
     * @param constants      Pre-converted values for constant arguments
     * @param isConstant     Which arguments are constant
     */
    public Call(Token token, ScriptFunction function, Expression[] arguments, Token[] argumentTokens, Object[] constants, boolean[] isConstant) {
        this.token = token;
        this.function = function;
        this.arguments = arguments;
        this.argumentTokens = argumentTokens;
        this.constants = constants;
        this.isConstant = isConstant;
    }

    public Token token() {
        return this.token;
    }

    public ScriptFunction function() {
        return this.function;
    }

    public int argumentCount() {
        return this.arguments.length;
    }

    /**
     * @return The argument expression at the index
     */
    public Expression argument(int index) {
        return this.arguments[index];
    }

    /**
     * @return True if the argument is a constant, already converted when the script was compiled
     */
    public boolean isConstant(int index) {
        return this.isConstant[index];
    }

    /**
     * @return The converted value of a constant argument
     */
    public Object constant(int index) {
        return this.constants[index];
    }

    /**
     * Converts a value for the argument at the index, reporting a problem at the argument's location.
     */
    public Object convert(int index, Object value) {
        return convertArgument(this.function, index, value, this.argumentTokens[index]);
    }

    @Override
    public Object eval() {
        if (this.function.lazy())
            return this.invoke(new LazyValues(this));
        if (this.arguments.length == 0)
            return this.invoke(NO_ARGUMENTS);

        // A fresh array per evaluation. Compiled expressions are cached and shared, so reusing a single
        // array would race across threads and could be mutated by a handler that holds on to it.
        var values = new Object[this.arguments.length];
        for (int i = 0; i < values.length; i++)
            values[i] = this.argumentValue(i);
        return this.invoke(new Values(values));
    }

    private Object argumentValue(int index) {
        if (this.isConstant[index])
            return this.constants[index];
        return convertArgument(this.function, index, this.arguments[index].eval(), this.argumentTokens[index]);
    }

    private Object invoke(ScriptArguments args) {
        try {
            return this.function.handler().evaluate(args);
        } catch (ScriptException e) {
            // Handlers typically fail inside ScriptHelpers, which has no location. Point at the call.
            throw ScriptException.locate(this.token, e);
        }
    }

    /**
     * Converts an argument to its parameter type, reporting a problem at the argument's location.
     */
    public static Object convertArgument(ScriptFunction function, int index, Object value, Token argumentToken) {
        var type = function.parameterType(index);
        if (type.isPassThrough())
            return value;

        Object converted;
        String reason = null;
        try {
            converted = type.convert(value);
        } catch (ScriptException e) {
            converted = null;
            reason = e.getReason();
        }
        if (converted == null) {
            if (reason == null)
                reason = "argument %d must be a %s".formatted(index + 1, type.name());
            ScriptException.throwException(argumentToken, function.name() + ": " + reason);
        }
        return converted;
    }

    @Override
    public @NotNull String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.token.lexeme())
                .add("args", this.arguments.length)
                .toString();
    }

    private static final class Values extends ScriptArguments {
        private final Object[] values;

        Values(Object[] values) {
            this.values = values;
        }

        @Override
        public int count() {
            return this.values.length;
        }

        @Override
        public Object value(int index) {
            return this.values[index];
        }
    }

    private static final class LazyValues extends ScriptArguments {
        private final Call call;
        private final Object[] values;
        private final boolean[] evaluated;

        LazyValues(Call call) {
            this.call = call;
            this.values = new Object[call.arguments.length];
            this.evaluated = new boolean[call.arguments.length];
        }

        @Override
        public int count() {
            return this.values.length;
        }

        @Override
        public Object value(int index) {
            if (!this.evaluated[index]) {
                this.values[index] = this.call.argumentValue(index);
                this.evaluated[index] = true;
            }
            return this.values[index];
        }
    }
}
