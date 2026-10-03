package org.orecruncher.dsurround.lib.version;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * A version check couldn't be done: the information couldn't be fetched or read. Unchecked, so it can be thrown
 * from a {@link java.util.concurrent.CompletableFuture} task.
 */
public class VersionCheckException extends RuntimeException {

    public VersionCheckException(String message) {
        super(message);
    }

    public VersionCheckException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * A one-line description of why a check failed, for the log or chat. Looks through the wrappers a
     * {@link java.util.concurrent.CompletableFuture} adds.
     */
    public static String describe(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null)
            t = t.getCause();
        if (t instanceof TimeoutException)
            return "the check timed out";
        var message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }
}
