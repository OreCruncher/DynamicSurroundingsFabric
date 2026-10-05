package org.orecruncher.dsurround.lib.random;

/**
 * The finalization mix ("fmix") from MurmurHash3: scrambles the bits of a value so that inputs that differ by a
 * single bit, or are consecutive, give results that look unrelated. Useful for turning an id into a seed. This is
 * only the last step of MurmurHash3, not the full hash of a byte sequence.
 *
 * @see "https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp"
 */
public final class MurmurHash3 {
    private MurmurHash3() {
    }

    /**
     * Mixes the bits of an int (fmix32). Zero mixes to zero.
     */
    public static int hash(int k) {
        k ^= k >>> 16;
        k *= 0x85ebca6b;
        k ^= k >>> 13;
        k *= 0xc2b2ae35;
        k ^= k >>> 16;
        return k;
    }

    /**
     * Mixes the bits of a long (fmix64). Zero mixes to zero.
     */
    public static long hash(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;

        return k;
    }
}
