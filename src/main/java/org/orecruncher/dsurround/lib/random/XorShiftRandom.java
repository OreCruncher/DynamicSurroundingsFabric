package org.orecruncher.dsurround.lib.random;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Proxies the RandomGenerator from modern Java with a Random signature.
 */
public final class XorShiftRandom extends Random {

    private static final ThreadLocal<XorShiftRandom> localRandom = ThreadLocal.withInitial(XorShiftRandom::new);

    private final RandomGenerator generator;

    public XorShiftRandom() {
        this(0);
    }

    public XorShiftRandom(final long ignore) {
        // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html
        this.generator = RandomGenerator.of("Xoroshiro128PlusPlus");
    }

    public static XorShiftRandom current() {
        return localRandom.get();
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
