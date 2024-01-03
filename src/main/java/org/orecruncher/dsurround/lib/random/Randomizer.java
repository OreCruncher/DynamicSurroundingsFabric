package org.orecruncher.dsurround.lib.random;

/**
 * Pluggable randomizer instances to be used. At the time of this checking, the Java randomizer "Xoroshiro128PlusPlus"
 * provides the best performance. The Minecraft randomizer is next, followed by the legacy Random implementation. Given
 * the number of randoms generated per tick, I sweat these details.
 */
@SuppressWarnings("unused")
public class Randomizer {
    private static final ThreadLocal<IRandomizer> JAVA_LOCAL_RANDOM = ThreadLocal.withInitial(JavaRandomizer::new);
    private static final ThreadLocal<IRandomizer> MINECRAFT_LOCAL_RANDOM = ThreadLocal.withInitial(MinecraftRandomizer::new);

    public static IRandomizer current() {
        return MINECRAFT_LOCAL_RANDOM.get();
    }

    private Randomizer() {

    }

}
