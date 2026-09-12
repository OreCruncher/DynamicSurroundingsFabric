package org.orecruncher.dsurround.lib.random;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Simple wrapper for an IRandomizer instance that ensures that the
 * executing thread is the one that created the random instance.
 */
class CheckedRandomizer implements IRandomizer {

    private final IRandomizer randomizer;
    private final Thread owningThread;

    public CheckedRandomizer(IRandomizer randomizer) {
        this.randomizer = randomizer;
        this.owningThread = Thread.currentThread();
    }

    @Override
    public @NotNull RandomSource fork() {
        checkThreadAccess();
        return this.randomizer.fork();
    }

    @Override
    public @NotNull PositionalRandomFactory forkPositional() {
        checkThreadAccess();
        return this.randomizer.forkPositional();
    }

    @Override
    public void setSeed(long l) {
        checkThreadAccess();
        this.randomizer.setSeed(l);
    }

    @Override
    public int nextInt() {
        checkThreadAccess();
        return this.randomizer.nextInt();
    }

    @Override
    public int nextInt(int i) {
        checkThreadAccess();
        return this.randomizer.nextInt(i);
    }

    @Override
    public long nextLong() {
        checkThreadAccess();
        return this.randomizer.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        checkThreadAccess();
        return this.randomizer.nextBoolean();
    }

    @Override
    public float nextFloat() {
        checkThreadAccess();
        return this.randomizer.nextFloat();
    }

    @Override
    public double nextDouble() {
        checkThreadAccess();
        return this.randomizer.nextDouble();
    }

    @Override
    public double nextGaussian() {
        checkThreadAccess();
        return this.randomizer.nextGaussian();
    }

    private void checkThreadAccess() {
        if (this.owningThread != Thread.currentThread())
            throw new RuntimeException("Attempt to use random by a thread that did not create the instance");
    }
}
