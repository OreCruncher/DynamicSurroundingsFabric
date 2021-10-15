package org.orecruncher.dsurround.lib;

/**
 * Generalization of object matching logic.
 */
public interface IMatcher<T> {

    /**
     * Determines if the matcher is empty and can be optimized out of any processing.
     * @return true if the matcher can be optimized out, false otherwise
     */
    default boolean isEmpty() {
        return false;
    }

    /**
     * Access whether the given object matches the criteria of the IMatcher implementation.
     * @param object Object to test
     * @return true if the object matches the criteria, false otherwise
     */
    boolean match(T object);
}
