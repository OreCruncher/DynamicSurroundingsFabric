package org.orecruncher.dsurround.lib.scripting;

@FunctionalInterface
public interface IScriptFunction {

    /**
     * Evaluates the given arguments and returns back a result.
     * 
     * @param arguments Arguments for the function call
     * @return Results of the function execution
     */
    Object evaluate(Object[] arguments);
}
