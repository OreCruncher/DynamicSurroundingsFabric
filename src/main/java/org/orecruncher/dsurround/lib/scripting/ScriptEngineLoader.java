package org.orecruncher.dsurround.lib.scripting;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

class ScriptEngineLoader {
    public static ScriptEngine getEngine() {
        try {
            return NashornScriptEngineLoader.getEngine();
        } catch (final Throwable ignore) {
        }
        return new ScriptEngineManager().getEngineByName("JavaScript");
    }
}