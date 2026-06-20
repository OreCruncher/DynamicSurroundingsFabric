package org.orecruncher.dsurround.lib.scripting;

import com.mojang.serialization.Codec;

public class Script {

    public static final Codec<Script> CODEC = Codec.STRING.xmap(Script::new, (script) -> script.script);

    /**
     * Default script that always returns true.
     */
    public static final Script TRUE = new Script("true");

    private final String script;

    public Script(String script) {
        this.script = script;
    }

    /**
     * Obtains the string version of the script for evaluation.
     * @return The script to evaluate.
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
