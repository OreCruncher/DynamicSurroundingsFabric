package org.orecruncher.dsurround.lib.scripting;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.scripting.engine.ExpressionTree;

import javax.script.CompiledScript;
import java.util.Optional;

public class Script {

    public static final Codec<Script> CODEC = Codec.STRING.xmap(Script::new, (script) -> script.script);

    /**
     * Default script that always returns true.
     */
    public static final Script TRUE = new Script("true");

    private final String script;
    private ExpressionTree compiledScript;

    public Script(String script) {
        this.script = script;
    }

    /**
     * Retrieves the result of a previous compilation if present.
     * @return Compiled script, if any.
     */
    Optional<ExpressionTree> getCompiledScript() {
        return Optional.ofNullable(this.compiledScript);
    }

    /**
     * Sets the state of the script with the result of a previous compilation.
     * @param compiled Compiled script to cache
     */
    void setCompiledScript(@Nullable ExpressionTree compiled) {
        this.compiledScript = compiled;
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
}
