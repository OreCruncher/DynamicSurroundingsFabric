package org.orecruncher.dsurround.lib.random;

public class HashUtil {

    public static int combineHashes(int hash1, int hash2) {
        hash1 ^= hash2 + 0x9e3779b9 + (hash1 << 6) + (hash1 >> 2);
        return hash1;
    }
}

