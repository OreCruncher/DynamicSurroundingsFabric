package org.orecruncher.dsurround.lib.random;

/**
 * Bit mixers: functions that scramble the bits of a value so that inputs that differ by a single bit, or are
 * consecutive, give results that look unrelated. Useful for turning an id or a counter into a seed, or into a hash
 * that spreads evenly. They are not cryptographic.
 */
public final class Mixers {

    // The golden ratio as a 64-bit fraction: SplitMix64's step
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Mixers() {
    }

    /**
     * MurmurHash3's 32-bit finalization mix. This is only the last step of MurmurHash3, not the full hash of a byte
     * sequence. Zero mixes to zero.
     *
     * @see "https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp"
     */
    public static int fmix32(int k) {
        k ^= k >>> 16;
        k *= 0x85ebca6b;
        k ^= k >>> 13;
        k *= 0xc2b2ae35;
        k ^= k >>> 16;
        return k;
    }

    /**
     * MurmurHash3's 64-bit finalization mix. This is only the last step of MurmurHash3, not the full hash of a byte
     * sequence. Zero mixes to zero.
     *
     * @see "https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp"
     */
    public static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    /**
     * Output number {@code index} (counting from 0) of SplitMix64 started from a seed of 0, worked out directly:
     * SplitMix64's state steps by the golden ratio, and each output is that state put through David Stafford's
     * "Mix13" variant of the 64-bit finalizer (the same mix as Minecraft's RandomSupport.mixStafford13). Gives
     * well-spread seeds from consecutive numbers, such as day numbers.
     *
     * @see "https://prng.di.unimi.it/splitmix64.c"
     */
    public static long splitMix64(long index) {
        var z = (index + 1) * GOLDEN_GAMMA;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
