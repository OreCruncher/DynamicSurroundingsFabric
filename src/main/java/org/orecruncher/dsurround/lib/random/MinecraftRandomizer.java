package org.orecruncher.dsurround.lib.random;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jetbrains.annotations.NotNull;

class MinecraftRandomizer implements IRandomizer {

    private final RandomSource source;

    public MinecraftRandomizer() {
        this(RandomSource.create());
    }

    public MinecraftRandomizer(RandomSource source) {
        this.source = source;
    }

    @Override
    public @NotNull RandomSource fork() {
        return this.source.fork();
    }

    @Override
    public @NotNull PositionalRandomFactory forkPositional() {
        return this.source.forkPositional();
    }

    @Override
    public void setSeed(long seed) {
        this.source.setSeed(seed);
    }

    @Override
    public int nextInt() {
        return this.source.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return this.source.nextInt(bound);
    }

    @Override
    public boolean nextBoolean() {
        return this.source.nextBoolean();
    }

    @Override
    public double nextDouble() {
        return this.source.nextDouble();
    }

    @Override
    public float nextFloat() {
        return this.source.nextFloat();
    }

    @Override
    public double nextGaussian() {
        return this.source.nextGaussian();
    }

    @Override
    public long nextLong() {
        return this.source.nextLong();
    }
}
