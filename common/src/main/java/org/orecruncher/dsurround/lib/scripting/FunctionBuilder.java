package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds a function definition. Declare the parameters in order, then call {@link #handler} to define the
 * function:
 * <pre>
 * config.function("math.round")
 *       .param(ArgType.NUMBER)
 *       .optional(ArgType.INTEGER)
 *       .pure()
 *       .handler(args -&gt; round(args.number(0), args.count() &gt; 1 ? args.integer(1) : 0));
 * </pre>
 */
public final class FunctionBuilder {

    private final String name;
    private final Consumer<ScriptFunction> sink;
    private final List<ArgType<?>> parameters = new ArrayList<>();
    private int required;
    private boolean hasOptional;
    private ArgType<?> varArgType;
    private boolean pure;
    private boolean lazy;
    private ICallCompiler compiler;

    FunctionBuilder(String name, Consumer<ScriptFunction> sink) {
        this.name = name;
        this.sink = sink;
    }

    /**
     * Adds a required parameter. Required parameters come before optional ones.
     */
    public FunctionBuilder param(@NotNull ArgType<?> type) {
        if (this.hasOptional || this.varArgType != null)
            throw new IllegalStateException("Required parameters must come before optional and variable parameters: " + this.name);
        this.parameters.add(type);
        this.required++;
        return this;
    }

    /**
     * Adds an optional parameter.
     */
    public FunctionBuilder optional(@NotNull ArgType<?> type) {
        if (this.varArgType != null)
            throw new IllegalStateException("Optional parameters must come before variable parameters: " + this.name);
        this.parameters.add(type);
        this.hasOptional = true;
        return this;
    }

    /**
     * Accepts any number of further arguments of the type (including none). To require at least one, declare a
     * {@link #param} of the same type first.
     */
    public FunctionBuilder varParams(@NotNull ArgType<?> type) {
        if (this.hasOptional)
            throw new IllegalStateException("A function cannot have both optional and variable parameters: " + this.name);
        this.varArgType = type;
        return this;
    }

    /**
     * The result depends only on the arguments (no game state, no randomness). Calls whose arguments are all
     * constants are evaluated once when the script is compiled.
     */
    public FunctionBuilder pure() {
        this.pure = true;
        return this;
    }

    /**
     * Arguments are evaluated only when the handler requests them, as for a conditional.
     */
    public FunctionBuilder lazy() {
        this.lazy = true;
        return this;
    }

    /**
     * Compiles calls to a specialized node instead of the standard call. The handler is still used when a pure
     * function is evaluated at compile time.
     */
    public FunctionBuilder compileWith(@NotNull ICallCompiler compiler) {
        this.compiler = compiler;
        return this;
    }

    /**
     * Sets the implementation and defines the function.
     */
    public void handler(@NotNull IScriptFunctionHandler handler) {
        this.sink.accept(new ScriptFunction(this.name, List.copyOf(this.parameters), this.required, this.varArgType,
                this.pure, this.lazy, handler, this.compiler));
    }
}
