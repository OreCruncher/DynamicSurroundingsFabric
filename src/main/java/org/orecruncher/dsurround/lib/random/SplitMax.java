package org.orecruncher.dsurround.lib.random;

import java.time.Instant;

/**
 * RNG that is used to seed the other random implementations.
 */
@SuppressWarnings("unused")
public final class SplitMax {

    private static final double TWOPOWER64 = Math.pow(2.0, 64);
    private static final long PRIME = 402653189;

    private static final ThreadLocal<SplitMax> LOCAL_RANDOM = ThreadLocal.withInitial(SplitMax::new);
    private long state;

    public SplitMax() {
        this(Instant.now().getNano() * PRIME);
    }

    public SplitMax(final long seed) {
        this.state = seed;
    }

    public static SplitMax current() {
        return LOCAL_RANDOM.get();
    }

    public long next() {
        long z = (this.state += 0x9e3779b97f4a7c15L);
        z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >> 31);
    }

    public float nextFloat() {
        return (float) (this.next() / TWOPOWER64);
    }

    public int nextInt(int bound) {
        return (int) (this.next() % bound);
    }
}
