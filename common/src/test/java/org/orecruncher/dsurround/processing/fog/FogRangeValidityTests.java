package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.fog.FogData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which fog ranges from the calculators are used.
 */
public class FogRangeValidityTests {

    private static FogData range(float start, float end) {
        var data = new FogData();
        data.environmentalStart = start;
        data.environmentalEnd = end;
        return data;
    }

    @Test
    void aStartBehindTheCameraIsValid() {
        // Regression: vanilla starts the fog behind the camera under water (-8), and the calculators hand on the range
        // they're given; each was rejected, and warned of three times a frame
        assertTrue(HolisticFogRangeCalculator.isValid(range(-8F, 96F)));
        assertTrue(HolisticFogRangeCalculator.isValid(range(-0.846F, 44.685F)));
    }

    @Test
    void ordinaryRangesAreValid() {
        assertTrue(HolisticFogRangeCalculator.isValid(range(0F, 192F)));
        assertTrue(HolisticFogRangeCalculator.isValid(range(32F, 32F)));
    }

    @Test
    void brokenRangesAreNot() {
        assertFalse(HolisticFogRangeCalculator.isValid(range(50F, 10F)), "start past the end");
        assertFalse(HolisticFogRangeCalculator.isValid(range(-10F, -1F)), "end behind the camera");
        assertFalse(HolisticFogRangeCalculator.isValid(range(Float.NaN, 10F)), "NaN");
        assertFalse(HolisticFogRangeCalculator.isValid(range(0F, Float.POSITIVE_INFINITY)), "infinite");
    }
}
