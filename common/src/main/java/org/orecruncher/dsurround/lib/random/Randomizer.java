package org.orecruncher.dsurround.lib.random;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Pluggable randomizer instances to be used by application logic. The abstraction allows for the underlying
 * randomization routines to change without rippling up into the application.
 */
public final class Randomizer implements IRandomizer {
    private static final ThreadLocal<IRandomizer> THREAD_LOCAL = ThreadLocal.withInitial(Randomizer::getRandomizer);

    /**
     * Reusable instance that wraps a ThreadLocal. Guards against multiple threads trying to use the same
     * concrete randomizer.
     */
    private static final IRandomizer SHARED = new Randomizer();

    /**
     * Returns a shared instance of the default randomizer for the currently executing thread.
     */
    public static IRandomizer current() {
        return SHARED;
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

    @Override
    public void setSeed(long l) {
        THREAD_LOCAL.get().setSeed(l);
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
        // The MinecraftRandomizer instance uses the XorShift random class from level
        // gen.
        return new CheckedRandomizer(new MinecraftRandomizer());
    }
}
