package org.orecruncher.dsurround.lib.scripting;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;

class NashornScriptEngineLoader {
    public static ScriptEngine getEngine() {
        // Do this to limit scope of access by the engine
        return new NashornScriptEngineFactory().getScriptEngine(s -> false);
    }
}