package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

public interface IConfigureDefinition {

    /**
     * Defines a function from a complete description. Most callers use {@link #function(String)} instead.
     * @param function Description of the function, including its implementation
     */
    void defineFunction(@NotNull ScriptFunction function);

    /**
     * Starts defining a function with typed parameters. Finish with {@link FunctionBuilder#handler}:
     * <pre>
     * config.function("math.pow")
     *       .param(ArgType.NUMBER).param(ArgType.NUMBER)
     *       .pure()
     *       .handler(args -&gt; Math.pow(args.number(0), args.number(1)));
     * </pre>
     * @param name Name of the function as used in scripts
     * @return Builder for the function's parameters and options
     */
    default FunctionBuilder function(@NotNull String name) {
        return new FunctionBuilder(name, this::defineFunction);
    }

    /**
     * Defines a function that takes no arguments and returns a value, such as {@code player.isFlying()}.
     * @param name   Name of the function
     * @param getter Supplies the current value
     */
    default void property(@NotNull String name, @NotNull Supplier<?> getter) {
        this.function(name).handler(args -> getter.get());
    }

    /**
     * Defines a pure function of one number, such as {@code math.sqrt}.
     */
    default void numberFunction(@NotNull String name, @NotNull DoubleUnaryOperator operator) {
        this.function(name)
                .param(ArgType.NUMBER)
                .pure()
                .handler(args -> operator.applyAsDouble(args.number(0)));
    }

    /**
     * Defines a pure function of two numbers, such as {@code math.pow}.
     */
    default void numberFunction(@NotNull String name, @NotNull DoubleBinaryOperator operator) {
        this.function(name)
                .param(ArgType.NUMBER).param(ArgType.NUMBER)
                .pure()
                .handler(args -> operator.applyAsDouble(args.number(0), args.number(1)));
    }

    /**
     * Defines a variable reference with an associated delegate that retrieves the value
     * as needed. Once defined the variable does not need further updates as any changes
     * are captured by using the delegate.
     *
     * @param name      Name of the variable to set
     * @param delegate  The delegate use to retrieve the current variable state.
     */
    void defineVariable(@NotNull String name,  @NotNull IScriptVariable delegate);
}
