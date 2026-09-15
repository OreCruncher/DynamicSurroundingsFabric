package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.scripting.*;

public final class ScriptEngine implements IConfigureDefinition {

    final Environment environment;
    final Compiler compiler;

    public ScriptEngine() {
        this.environment = new Environment();
        this.compiler = new Compiler(this.environment);
        LibraryFunctions.configure(this);
        MathFunctions.configure(this);
    }

    /**
     * Compiles the script into a syntax tree that can be used for evaluation. Expression instances should be cached
     * for performance.
     * @param script The script compile
     * @return An expression tree use to execute the script logic and obtain a value
     */
    public Expression compile(String script) {
        Preconditions.checkNotNull(script);
        return this.compiler.compile(script);
    }

    @Override
    public void defineVariable(@NotNull String name, @NotNull IScriptVariable delegate) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(delegate);
        this.environment.defineVariable(name, delegate);
    }

    @Override
    public void defineFunction(@NotNull String name, int arity, @NotNull IScriptFunction delegate) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(delegate);
        this.environment.defineFunction(name, arity, delegate);
    }

}
