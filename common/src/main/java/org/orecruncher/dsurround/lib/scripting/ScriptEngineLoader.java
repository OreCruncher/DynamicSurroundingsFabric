package org.orecruncher.dsurround.lib.scripting;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Optional;

class ScriptEngineLoader {
    public static Optional<ScriptEngine> getEngine() {
        try {
            return Optional.of(NashornScriptEngineLoader.getEngine());
        } catch (final Throwable ignore) {
        }
        return Optional.ofNullable(new ScriptEngineManager().getEngineByName("JavaScript"));
    }
}