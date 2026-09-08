package org.orecruncher.dsurround.lib.scripting.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScriptException extends RuntimeException {

    ScriptException(@NotNull String message) {
        super(message);
    }

    ScriptException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    static void error(Token token, String message) {
        error(token.line(), token.position(), message);
    }

    static void error(String message) {
        error(-1, -1, message);
    }

    static void error(int line, int position, String message) {
        report(line, position, "", message, null);
    }

    static void error(int line, int position, String message, Throwable cause) {
        report(line, position, "", message, cause);
    }

    private static void report(int line, int position, String where, String message, @Nullable Throwable throwable) {
        // Add 1 to position since it is 0 based.
        var text = String.format("[line %d, pos %d] Error%s: %s", line, position + 1, where, message);
        if (throwable != null) {
            throw new ScriptException(text, throwable);
        }
        throw new ScriptException(text);
    }
}
