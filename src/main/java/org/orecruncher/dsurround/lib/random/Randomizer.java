package org.orecruncher.dsurround.lib.random;

import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Proxies the RandomGenerator from modern Java with a Random signature.
 */
public final class Randomizer extends Random {

    // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html
    private static final String RNG_ALGORITHM = "Xoroshiro128PlusPlus";
    private static final ThreadLocal<Randomizer> LOCAL_RANDOM = ThreadLocal.withInitial(Randomizer::new);

    private final RandomGenerator generator;

    public Randomizer() {
        this.generator = RandomGeneratorFactory.of(RNG_ALGORITHM).create();
    }

    public Randomizer(final long seed) {
        this.generator = RandomGeneratorFactory.of(RNG_ALGORITHM).create(seed);
    }

    public static Randomizer current() {
        return LOCAL_RANDOM.get();
    }

    @Override
    public void nextBytes(final byte[] bytes) {
        this.generator.nextBytes(bytes);
    }

    @Override
    public int nextInt() {
        return this.generator.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return this.generator.nextInt(bound);
    }

    @Override
    public boolean nextBoolean() {
        return this.generator.nextBoolean();
    }

    @Override
    public double nextDouble() {
        return this.generator.nextDouble();
    }

    @Override
    public float nextFloat() {
        return this.generator.nextFloat();
    }

    @Override
    public double nextGaussian() {
        return this.generator.nextGaussian();
    }

    @Override
    public long nextLong() {
        return this.generator.nextLong();
    }
}
