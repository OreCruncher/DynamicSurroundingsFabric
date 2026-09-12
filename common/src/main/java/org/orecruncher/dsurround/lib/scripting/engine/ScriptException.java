package org.orecruncher.dsurround.lib.scripting.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.StringUtils;

public final class ScriptException extends RuntimeException {

    final int lineNumber;
    final int position;

    private ScriptException(@NotNull String message, int lineNumber, int position) {
        super(message);
        this.lineNumber = lineNumber;
        this.position = position;
    }

    private ScriptException(@NotNull String message, @NotNull Throwable throwable, int lineNumber, int position) {
        super(message, throwable);
        this.lineNumber = lineNumber;
        this.position = position;
    }

    public String getMessageForLogging() {
        return this.getMessageForLogging(null);
    }

    public String getMessageForLogging(@Nullable String script) {
        if (script != null && this.position != -1) {
            var locus = StringUtils.truncateWithCarat(script, this.position);
            return "Script error: %s\n%s\n%s".formatted(this.getMessage(), locus.text(), locus.caratLine());
        }
        return "Script error: %s".formatted(this.getMessage());
    }

    public Expression asExpression() {
        var msg = this.getMessageForLogging();
        return new Expression(null) {
            @Override
            public Object eval() {
                return msg;
            }
        };
    }

    public Expression asExpression(@Nullable String script) {
        var msg = this.getMessageForLogging(script);
        return new Expression(null) {
            @Override
            public Object eval() {
                return msg;
            }
        };
    }

    static void throwException(Token token, String message) throws ScriptException {
        throwException(token.line(), token.position(), message);
    }

    static void throwException(String message) throws ScriptException {
        throwException(-1, -1, message);
    }

    static void throwException(int line, int position, String message) throws ScriptException  {
        report(line, position, message, null);
    }

    private static void report(int line, int position, String message, @Nullable Throwable throwable) {
        // Add 1 to position since it is 0 based.
        var text = String.format("(%d, %d) %s", line, position + 1, message);
        if (throwable != null) {
            throw new ScriptException(text, throwable, line, position);
        }
        throw new ScriptException(text, line, position);
    }
}
