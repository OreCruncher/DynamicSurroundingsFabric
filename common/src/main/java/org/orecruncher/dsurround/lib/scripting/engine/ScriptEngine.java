package org.orecruncher.dsurround.lib.scripting.engine;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.*;

public class ScriptEngine implements IConfigureDefinition {

    final Environment environment;
    final LibraryFunctions libraryFunctions;
    final Compiler compiler;

    public ScriptEngine() {
        this.libraryFunctions = ContainerManager.resolve(LibraryFunctions.class);
        this.environment = new Environment();
        this.compiler = new Compiler(this.environment);
        this.libraryFunctions.configure(this);
    }

    /**
     * Compiles the script into a syntax tree that can be used for evaluation. This result should be cached
     * for performance.
     * @param script The script compile
     * @return A scriptlet use to execute the script logic and obtain a value
     */
    public ExpressionTree compile(String script) {
        return this.compiler.compile(script);
    }

    /**
     * Defines a variable reference with an associated delegate that retrieves the value
     * as needed. Once defined the variable does not need further updates as any changes
     * are captured by using the delegate.
     * @param variableName Name of the variable to set
     * @param delegate The delegate use to retrieve the current variable state.
     */
    @Override
    public void defineVariable(String variableName, @NotNull IScriptVariable delegate) {
        this.environment.defineVariable(variableName, delegate);
    }

    /**
     * Defines a function reference with associate delegate that implements the function.
     * @param name Name of the function
     * @param arity The number of parameters the function expects. A negative value indicates a variable number of parameters but with a certain minimum required.
     * @param delegate The delegate to use when executing the function
     */
    @Override
    public void defineFunction(String name, int arity, @NotNull IScriptFunction delegate) {
        this.environment.defineFunction(name, arity, delegate);
    }

}
