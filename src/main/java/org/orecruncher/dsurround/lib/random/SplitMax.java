package org.orecruncher.dsurround.lib.random;

import java.time.Instant;

/**
 * RNG that is used to seed the other random implementations.
 */
final class SplitMax {

    private static final long PRIME = 402653189;

    private static final ThreadLocal<SplitMax> localRandom = ThreadLocal.withInitial(SplitMax::new);
    private long x;

    private SplitMax() {
        this(Instant.now().getNano() * PRIME);
    }

    private SplitMax(final long seed) {
        this.x = seed;
    }

    public static SplitMax current() {
        return localRandom.get();
    }

    public long next() {
        long z = (this.x += 0x9e3779b97f4a7c15L);
        z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >> 31);
    }
}
