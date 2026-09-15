package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.NotNull;

public interface IConfigureDefinition {

    /**
     * Defines a function reference with associate delegate that implements the function.
     * @param name      Name of the function
     * @param arity     The number of parameters the function expects. A negative value indicates a variable number of parameters but with a certain minimum required.
     * @param delegate  The delegate that implements the function
     */
    void defineFunction(@NotNull String name, int arity, @NotNull IScriptFunction delegate);

    /**
     * Defines a function reference with associate delegate that implements the function. The function has an arity of 0,
     * meaning that it takes no input parameters.
     * @param name      Name of the function
     * @param delegate  The delegate that implements the function
     */
    default void defineFunction(@NotNull String name, @NotNull IScriptFunction delegate) {
        this.defineFunction(name, 0, delegate);
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
