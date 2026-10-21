package org.orecruncher.dsurround.lib.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the on/off switch with a gap between switching on and off.
 */
public class HysteresisTests {

    @Test
    void startsOff() {
        assertFalse(new Hysteresis(70F, 55F).isOn());
    }

    @Test
    void switchesOnAtTheUpperThreshold() {
        var latch = new Hysteresis(70F, 55F);
        assertFalse(latch.update(69F));
        assertTrue(latch.update(70F));
        assertTrue(latch.isOn());
    }

    @Test
    void staysOnUntilBelowTheLowerThreshold() {
        var latch = new Hysteresis(70F, 55F);
        latch.update(80F);
        assertTrue(latch.update(60F));
        assertTrue(latch.update(55F));
        assertFalse(latch.update(54F));
        // And it takes the upper threshold again to come back on
        assertFalse(latch.update(65F));
    }

    @Test
    void aValueWaveringAroundTheUpperThresholdDoesNotFlicker() {
        // Regression: the storm wind loop started and stopped as the wind went back and forth over 70%
        var latch = new Hysteresis(70F, 55F);
        boolean last = false;
        int flips = 0;
        for (int tick = 0; tick < 200; tick++) {
            var now = latch.update(tick % 2 == 0 ? 73F : 67F);
            if (now != last)
                flips++;
            last = now;
        }
        assertEquals(1, flips);
    }

    @Test
    void resetSwitchesItOff() {
        var latch = new Hysteresis(70F, 55F);
        latch.update(90F);
        latch.reset();
        assertFalse(latch.isOn());
    }

    @Test
    void withNoGapItIsAPlainThreshold() {
        var latch = new Hysteresis(50F, 50F);
        assertTrue(latch.update(50F));
        assertFalse(latch.update(49.9F));
    }

    @Test
    void theOffThresholdCannotBeAboveTheOn() {
        assertThrows(IllegalArgumentException.class, () -> new Hysteresis(50F, 60F));
    }
}
