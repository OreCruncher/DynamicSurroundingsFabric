package org.orecruncher.dsurround.lib.random;

import java.util.function.Supplier;

/**
 * Pluggable randomizer instances to be used. At the time of this checking, the Java randomizer "Xoroshiro128PlusPlus"
 * provides the best performance. The Minecraft randomizer is next, followed by the legacy Random implementation. Given
 * the number of randoms generated per tick, I sweat these details.
 */
@SuppressWarnings("unused")
public class Randomizer {
    // Replace the Supplier with MinecraftRandomizer if the MC randomizer is desired
    private static final Supplier<IRandomizer> DEFAULT_RANDOMIZER = JavaRandomizer::new;
    private static final ThreadLocal<IRandomizer> SHARED = ThreadLocal.withInitial(DEFAULT_RANDOMIZER);

    /**
     * Instantiates a new instance of the default randomizer.
     */
    public static IRandomizer create() {
        return DEFAULT_RANDOMIZER.get();
    }

    /**
     * Returns a shared instance of the default randomizer.
     */
    public static IRandomizer current() {
        return SHARED.get();
    }

    private Randomizer() {

    }

}
