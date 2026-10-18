package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.FogRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which fog ranges from the calculators are used.
 */
public class FogRangeValidityTests {

    private static FogRenderer.FogData range(float start, float end) {
        var data = new FogRenderer.FogData(FogRenderer.FogMode.FOG_TERRAIN);
        data.start = start;
        data.end = end;
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

    // ---- Fog another mod has turned off (#201)

    // Distant Horizons turns vanilla's fog off by starting it at the largest float, past its end
    private static FogRenderer.FogData turnedOff() {
        return range(Float.MAX_VALUE, 112F);
    }

    @Test
    void fogStartedPastItsEndIsTurnedOff() {
        assertTrue(HolisticFogRangeCalculator.isTurnedOff(turnedOff()));
        assertFalse(HolisticFogRangeCalculator.isTurnedOff(range(-8F, 96F)), "under water");
        assertFalse(HolisticFogRangeCalculator.isTurnedOff(range(32F, 32F)), "a wall, but on");
    }

    @Test
    void aRangeHandedBackUnchangedIsntJudged() {
        // Regression: the fog range Distant Horizons gives, handed on by a calculator with nothing to add, was
        // reported as that calculator's invalid range, over and over
        var data = turnedOff();
        assertFalse(HolisticFogRangeCalculator.changed(data, data));
        assertFalse(HolisticFogRangeCalculator.changed(range(Float.MAX_VALUE, 112F), data), "a copy, the same range");
        assertTrue(HolisticFogRangeCalculator.changed(range(10F, 112F), data));
    }
}
