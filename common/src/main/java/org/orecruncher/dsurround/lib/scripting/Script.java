package org.orecruncher.dsurround.lib.scripting;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Expression;

public class Script {

    public static final Codec<Script> CODEC = Codec.STRING.xmap(Script::new, (script) -> script.script);

    /**
     * Default script that always returns true.
     */
    public static final Script TRUE = new Script("true");

    private final String script;

    // Compiled expressions resolve variables and functions against a specific ExecutionContext, so the cache
    // records which context (and which version of its definitions) produced it. Held in a single immutable
    // record so readers on other threads always see a consistent triple.
    private volatile CompiledState compiledState;

    public Script(String script) {
        Preconditions.checkNotNull(script);
        this.script = script;
    }

    /**
     * Retrieves the result of a previous compilation if it was produced by the given context at the given
     * definition generation.
     * @param owner      Context performing the evaluation
     * @param generation Current definition generation of that context
     * @return Compiled script, or null if there is none for this context and generation.
     */
    @Nullable Expression getCompiledExpression(Object owner, int generation) {
        var state = this.compiledState;
        if (state != null && state.owner == owner && state.generation == generation)
            return state.expression;
        return null;
    }

    /**
     * Sets the state of the script with the result of a compilation.
     * @param owner      Context that compiled the script
     * @param generation Definition generation of that context at compile time
     * @param compiled   Compiled script to cache, or null to clear
     */
    void setCompiledScript(Object owner, int generation, @Nullable Expression compiled) {
        this.compiledState = compiled == null ? null : new CompiledState(owner, generation, compiled);
    }

    /**
     * Obtains the string version of the script for compilation
     * @return The script to be compiled.
     */
    public String asString() {
        return this.script;
    }

    @Override
    public String toString() {
        return this.script;
    }

    @Override
    public int hashCode() {
        return this.script.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Script s) {
            return s.script.equals(this.script);
        }
        return false;
    }

    private record CompiledState(Object owner, int generation, Expression expression) {
    }
}
