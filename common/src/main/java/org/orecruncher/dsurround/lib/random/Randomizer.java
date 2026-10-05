package org.orecruncher.dsurround.lib.random;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.NotNull;

/**
 * Pluggable randomizer instances to be used by application logic. The abstraction allows for the underlying
 * randomization routines to change without rippling up into the application.
 * <p>
 * The shared instance hands each call to a generator of the calling thread's own, so it can be kept in a static
 * field and used from any thread. A thread's generator is only reachable from that thread, through here.
 */
public final class Randomizer implements IRandomizer {
    private static final ThreadLocal<IRandomizer> THREAD_LOCAL = ThreadLocal.withInitial(Randomizer::getRandomizer);

    /**
     * Reusable instance that wraps a ThreadLocal. Guards against multiple threads trying to use the same
     * concrete randomizer.
     */
    private static final IRandomizer SHARED = new Randomizer();

    /**
     * Returns the shared randomizer. Safe to keep and use from any thread: each call uses the calling thread's own
     * generator.
     */
    public static IRandomizer current() {
        return SHARED;
    }

    /**
     * A new randomizer with a sequence of its own, starting from {@code seed}: the same seed always gives the same
     * numbers. Unlike the shared randomizer, it isn't safe to use from more than one thread at a time.
     */
    public static IRandomizer create(long seed) {
        return new MinecraftRandomizer(seed);
    }

    private Randomizer() {
    }

    @Override
    public @NotNull RandomSource fork() {
        return THREAD_LOCAL.get().fork();
    }

    @Override
    public @NotNull PositionalRandomFactory forkPositional() {
        return THREAD_LOCAL.get().forkPositional();
    }

    /**
     * Not supported: the calling thread's generator is shared by everything on that thread, so reseeding it would
     * make all of their randomness repeat. For a repeatable sequence, create one with {@link #create(long)}.
     */
    @Override
    public void setSeed(long l) {
        throw new UnsupportedOperationException("The shared randomizer can't be reseeded; use Randomizer.create(seed) for a repeatable sequence");
    }

    @Override
    public int nextInt() {
        return THREAD_LOCAL.get().nextInt();
    }

    @Override
    public int nextInt(int i) {
        return THREAD_LOCAL.get().nextInt(i);
    }

    @Override
    public long nextLong() {
        return THREAD_LOCAL.get().nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return THREAD_LOCAL.get().nextBoolean();
    }

    @Override
    public float nextFloat() {
        return THREAD_LOCAL.get().nextFloat();
    }

    @Override
    public double nextDouble() {
        return THREAD_LOCAL.get().nextDouble();
    }

    @Override
    public double nextGaussian() {
        return THREAD_LOCAL.get().nextGaussian();
    }

    private static IRandomizer getRandomizer() {
        // The MinecraftRandomizer instance uses the Xoroshiro random class from level gen
        return new MinecraftRandomizer();
    }
}
