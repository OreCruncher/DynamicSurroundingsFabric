package org.orecruncher.dsurround.runtime.audio;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The {@link ISourceProvider}s the enhanced sound processor asks for sounds. Apart from {@link EnhancedSoundProcessor}, so
 * an integration can register while its mod starts, before the processor is set up.
 */
public final class SourceProviders {

    // Registered from any thread, read by the sound processor's
    private static final List<ISourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private SourceProviders() {
    }

    /**
     * Adds a provider; it stays for the life of the game.
     */
    public static void register(ISourceProvider provider) {
        PROVIDERS.add(provider);
    }

    /**
     * Every provider's sounds due a recalculation.
     */
    static void forEachDue(Consumer<Callable<?>> action) {
        for (var provider : PROVIDERS)
            provider.forEachDue(action);
    }
}
