package org.orecruncher.dsurround.lib.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MixersTests {

    @Test
    void fmixMatchesTheReference() {
        // fmix32 and fmix64 from MurmurHash3, worked out independently
        assertEquals(0, Mixers.fmix32(0));
        assertEquals(0x514E28B7, Mixers.fmix32(1));
        assertEquals(0x30F4C306, Mixers.fmix32(2));
        assertEquals(0x81F16F39, Mixers.fmix32(-1));
        assertEquals(0L, Mixers.fmix64(0L));
        assertEquals(0xB456BCFC34C2CB2CL, Mixers.fmix64(1L));
        assertEquals(0x64B5720B4B825F21L, Mixers.fmix64(-1L));
    }

    @Test
    void splitMix64MatchesTheReference() {
        // The first outputs of SplitMix64 started from 0, worked out by stepping it the usual way
        assertEquals(0xE220A8397B1DCDAFL, Mixers.splitMix64(0));
        assertEquals(0x6E789E6AA1B965F4L, Mixers.splitMix64(1));
        assertEquals(0x06C45D188009454FL, Mixers.splitMix64(2));
        // Index -1 is SplitMix64's starting state, 0, which mixes to 0
        assertEquals(0L, Mixers.splitMix64(-1));
    }

    @Test
    void consecutiveValuesComeOutUnrelated() {
        // On average about half the bits differ between the results for neighbouring inputs
        long differing32 = 0;
        long differing64 = 0;
        long differingSplit = 0;
        for (int i = 0; i < 1000; i++) {
            differing32 += Integer.bitCount(Mixers.fmix32(i) ^ Mixers.fmix32(i + 1));
            differing64 += Long.bitCount(Mixers.fmix64(i) ^ Mixers.fmix64(i + 1L));
            differingSplit += Long.bitCount(Mixers.splitMix64(i) ^ Mixers.splitMix64(i + 1L));
        }
        assertEquals(16.0, differing32 / 1000.0, 1.5);
        assertEquals(32.0, differing64 / 1000.0, 2.0);
        assertEquals(32.0, differingSplit / 1000.0, 2.0);
    }
}
