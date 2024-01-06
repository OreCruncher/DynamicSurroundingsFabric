package org.orecruncher.dsurround.lib.random;

@SuppressWarnings("unused")
public class HashUtil {

    public static int combineHashes(int hash1, int hash2) {
        hash1 ^= hash2 + 0x9e3779b9 + (hash1 << 6) + (hash1 >> 2);
        return hash1;
    }

    public static int combineHashes(int hash1, int hash2, int hash3) {
        var hash = combineHashes(hash1, hash2);
        return combineHashes(hash, hash3);
    }

    public static int combineHashes(int hash1, int hash2, int hash3, int hash4) {
        var hash = combineHashes(hash1, hash2);
        hash = combineHashes(hash, hash3);
        return combineHashes(hash, hash4);
    }
}

