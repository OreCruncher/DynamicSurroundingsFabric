package org.orecruncher.dsurround.lib.di;

/**
 * Thrown when the container can't register or create something. The message says which class and why; when a
 * constructor or supplier threw, that exception is the cause (not wrapped further).
 */
public class DependencyException extends RuntimeException {

    public DependencyException(String message) {
        super(message);
    }

    public DependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
