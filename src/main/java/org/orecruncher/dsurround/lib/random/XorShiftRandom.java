package org.orecruncher.dsurround.lib.random;

import org.orecruncher.dsurround.lib.math.MathStuff;

import java.util.Random;

/**
 * @see "http://xoroshiro.di.unimi.it/xoroshiro128plus.c"
 */
public final class XorShiftRandom extends Random {

    private static final double DOUBLE_UNIT = 0x1.0p-53; // 1.0 / (1L << 53);
    private static final float FLOAT_UNIT = 0x1.0p-24f; // 1.0 / (1L << 24);

    private static final int A = 24;
    private static final int B = 16;
    private static final int C = 37;

    private static final ThreadLocal<XorShiftRandom> localRandom = ThreadLocal.withInitial(XorShiftRandom::new);

    private long s0;
    private long s1;
    private boolean hasGaussian;
    private double nextGaussian;

    public XorShiftRandom() {
        this(SplitMax.current().next());
    }

    public XorShiftRandom(final long seed) {
        super(0);
        setSeed0(seed);
    }

    public static Random current() {
        return localRandom.get();
    }

    private void setSeed0(final long seed) {
        this.s0 = seed == 0 ? 0xdeadbeefL : seed;
        this.s1 = MurmurHash3.hash(this.s0);
    }

    @Override
    public void setSeed(final long seed) {
        setSeed0(seed);
        this.hasGaussian = false;
    }

    @Override
    public void nextBytes(final byte[] bytes) {
        for (int i = 0, len = bytes.length; i < len; ) {
            long rnd = nextInt();
            for (int n = Math.min(len - i, 8); n-- > 0; rnd >>>= 8) {
                bytes[i++] = (byte) rnd;
            }
        }
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    @Override
    public float nextFloat() {
        return (nextInt() >>> 8) * FLOAT_UNIT;
    }

    @Override
    public int nextInt() {
        return (int) nextLong();
    }

    // https://en.wikipedia.org/wiki/Marsaglia_polar_method
    private double genGaussian() {
        double v1, v2, s;
        do {
            v1 = 2 * nextDouble() - 1; // between -1 and 1
            v2 = 2 * nextDouble() - 1; // between -1 and 1
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);

        final double multiplier = Math.sqrt(-2 * MathStuff.log(s) / s);
        this.nextGaussian = v2 * multiplier;
        this.hasGaussian = true;
        return v1 * multiplier;
    }

    @Override
    public double nextGaussian() {
        // See Knuth, ACP, Section 3.4.1 Algorithm C.
        if (!this.hasGaussian)
            return genGaussian();
        this.hasGaussian = false;
        return this.nextGaussian;
    }

    @Override
    public long nextLong() {
        final long result = this.s0 + this.s1;
        final long s1 = this.s1 ^ this.s0;
        this.s0 = Long.rotateLeft(this.s0, A) ^ s1 ^ (s1 << B);
        this.s1 = Long.rotateLeft(s1, C);
        return result;
    }

    @Override
    protected int next(final int bits) {
        return ((int) nextLong()) >>> (32 - bits);
    }
}
