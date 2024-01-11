package org.orecruncher.dsurround.lib.random;

import org.orecruncher.dsurround.lib.Library;

import java.util.function.Supplier;
import java.util.random.RandomGeneratorFactory;

/**
 * Pluggable randomizer instances to be used. At the time of this checking, the Java randomizer "Xoroshiro128PlusPlus"
 * provides the best performance. The Minecraft randomizer is next, followed by the legacy Random implementation. Given
 * the number of randoms generated per tick, I sweat these details.
 */
@SuppressWarnings("unused")
public class Randomizer {
    private static final Supplier<IRandomizer> DEFAULT_RANDOMIZER = Randomizer::getRandomizer;
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

    private static IRandomizer getRandomizer() {
        try {
            Library.getLogger().info("Creating RandomGenerator '%s'", JavaRandomizer.XOROSHIRO_128_PLUS_PLUS);
            return new JavaRandomizer(JavaRandomizer.XOROSHIRO_128_PLUS_PLUS);
        } catch (Exception ex) {
            Library.getLogger().error(ex, "Unable to create randomizer!");
            Library.getLogger().info("RandomGenerator factories available:");

            RandomGeneratorFactory.all()
                    .map(RandomGeneratorFactory::name)
                    .sorted()
                    .forEach(Library.getLogger()::info);
        }

        Library.getLogger().info("Falling back to Minecraft randomizer");
        return new MinecraftRandomizer();
    }
}
