package org.orecruncher.dsurround.lib.scripting;

/**
 * Implements a function defined with {@link IConfigureDefinition#function(String)}.
 */
@FunctionalInterface
public interface IScriptFunctionHandler {

    /**
     * @param arguments Arguments, already converted to the declared parameter types
     * @return Result of the function
     */
    Object evaluate(ScriptArguments arguments);
}
