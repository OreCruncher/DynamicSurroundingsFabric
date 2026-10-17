package org.orecruncher.dsurround.runtime.audio;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Something with sounds for the enhanced sound processor to recalculate: the game's sounds, or an integration's
 * (Simple Voice Chat's voices). Each run of the processor asks every provider registered with
 * {@link SourceProviders} for its sounds due a recalculation, and runs those on the worker threads.
 */
@FunctionalInterface
public interface ISourceProvider {

    /**
     * Hands over each sound due a recalculation. Called by the sound processor's thread, once per run.
     */
    void forEachDue(Consumer<Callable<?>> action);
}
