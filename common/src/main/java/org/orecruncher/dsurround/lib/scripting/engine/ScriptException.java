package org.orecruncher.dsurround.lib.scripting.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.StringUtils;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Expression;

/**
 * Reports a problem with a script, either at compile time or during evaluation.
 * <p>
 * Stack traces are not captured. The location that matters is the position in the script, which is part of the
 * message; the Java stack only shows engine internals. Skipping the capture matters for performance because a
 * broken script is evaluated (and fails) every tick.
 */
public final class ScriptException extends RuntimeException {

    final String reason;
    final int lineNumber;
    final int column;
    final int position;

    private ScriptException(@NotNull String message, @NotNull String reason, int lineNumber, int column, int position) {
        super(message, null, false, false);
        this.reason = reason;
        this.lineNumber = lineNumber;
        this.column = column;
        this.position = position;
    }

    private ScriptException(@NotNull String message, @NotNull String reason, @NotNull Throwable throwable, int lineNumber, int column, int position) {
        super(message, throwable, false, false);
        this.reason = reason;
        this.lineNumber = lineNumber;
        this.column = column;
        this.position = position;
    }

    /**
     * @return The description of the problem without the location prefix.
     */
    public String getReason() {
        return this.reason;
    }

    /**
     * @return True if the exception identifies where in the script the problem is located.
     */
    public boolean hasLocation() {
        return this.lineNumber >= 0 && this.position >= 0;
    }

    /**
     * @return 1-based line number of the problem, or -1 if not known.
     */
    public int getLineNumber() {
        return this.hasLocation() ? this.lineNumber : -1;
    }

    /**
     * @return 1-based column of the problem within its line, or -1 if not known.
     */
    public int getColumnNumber() {
        return this.hasLocation() ? this.column + 1 : -1;
    }

    /**
     * @return 0-based offset of the problem from the start of the script, or -1 if not known.
     */
    public int getPosition() {
        return this.hasLocation() ? this.position : -1;
    }

    public String getMessageForLogging() {
        return this.getMessageForLogging(null);
    }

    public String getMessageForLogging(@Nullable String script) {
        if (script != null && this.position != -1) {
            // The carat is rendered using the absolute offset into the script
            var locus = StringUtils.truncateWithCarat(script, this.position);
            return "Script error: %s\n%s\n%s".formatted(this.getMessage(), locus.text(), locus.caratLine());
        }
        return "Script error: %s".formatted(this.getMessage());
    }

    public Expression asExpression() {
        var msg = this.getMessageForLogging();
        return () -> msg;
    }

    /**
     * Attaches a location to an exception that does not have one. Used by expression nodes so that errors raised
     * by value conversions or function handlers point at the operator, variable, or call that triggered them.
     * An exception that already has a location is returned unchanged so the innermost location is kept.
     */
    public static ScriptException locate(Token token, ScriptException e) {
        if (e.hasLocation())
            return e;
        var text = withLocation(token.line(), token.column(), e.reason);
        return new ScriptException(text, e.reason, e, token.line(), token.column(), token.position());
    }

    public static void throwException(Token token, String message) throws ScriptException {
        throwException(token.line(), token.column(), token.position(), message);
    }

    public static void throwException(String message) throws ScriptException {
        throwException(-1, -1, -1, message);
    }

    public static void throwException(int line, int column, int position, String message) throws ScriptException {
        report(line, column, position, message, null);
    }

    private static String withLocation(int line, int column, String message) {
        // Columns are 0 based internally; report them 1 based. (Plain concatenation; String.format is slow.)
        return "(" + line + ", " + (column + 1) + ") " + message;
    }

    private static void report(int line, int column, int position, String message, @Nullable Throwable throwable) {
        var text = line < 0 ? message : withLocation(line, column, message);
        if (throwable != null) {
            throw new ScriptException(text, message, throwable, line, column, position);
        }
        throw new ScriptException(text, message, line, column, position);
    }
}
