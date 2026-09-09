package org.orecruncher.dsurround.lib.scripting.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScriptException extends RuntimeException {

    final int lineNumber;
    final int position;

    ScriptException(@NotNull String message) {
        super(message);
        this.lineNumber = -1;
        this.position = -1;
    }

    ScriptException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
        this.lineNumber = -1;
        this.position = -1;
    }

    ScriptException(@NotNull String message, int lineNumber, int position) {
        super(message);
        this.lineNumber = lineNumber;
        this.position = position;
    }

    ScriptException(@NotNull String message, @NotNull Throwable throwable, int lineNumber, int position) {
        super(message, throwable);
        this.lineNumber = lineNumber;
        this.position = position;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }

    public int getPosition() {
        return this.position;
    }

    static void error(Token token, String message) {
        error(token.line(), token.position(), message);
    }

    static void error(String message) {
        error(-1, -1, message);
    }

    static void error(int line, int position, String message) {
        report(line, position, message, null);
    }

    static void error(int line, int position, String message, Throwable cause) {
        report(line, position, message, cause);
    }

    private static void report(int line, int position, String message, @Nullable Throwable throwable) {
        // Add 1 to position since it is 0 based.
        var text = String.format("[line %d, pos %d] Error: %s", line, position + 1, message);
        if (throwable != null) {
            throw new ScriptException(text, throwable, line, position);
        }
        throw new ScriptException(text, line, position);
    }
}
