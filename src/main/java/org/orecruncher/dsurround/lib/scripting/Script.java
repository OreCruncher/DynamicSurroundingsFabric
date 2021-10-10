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

    public String getScript() {
        return this.script;
    }
}
