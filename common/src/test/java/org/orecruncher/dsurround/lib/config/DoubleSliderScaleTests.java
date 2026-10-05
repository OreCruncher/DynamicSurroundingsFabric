package org.orecruncher.dsurround.lib.config;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for converting between a double slider's values and the positions of the integer slider that shows it.
 */
public class DoubleSliderScaleTests {

    @Test
    void positionsRunFromMinimumToMaximum() {
        var scale = new DoubleSliderScale(0.5D, 4D, 0.25D);

        assertEquals(14, scale.lastIndex());
        assertEquals(0.5D, scale.valueAt(0));
        assertEquals(1.5D, scale.valueAt(4));
        assertEquals(4D, scale.valueAt(14));
    }

    @Test
    void valuesHaveNoFloatingPointDrift() {
        // 3 * 0.1 in double arithmetic is 0.30000000000000004, which would end up in the file
        var scale = new DoubleSliderScale(0D, 1D, 0.1D);

        assertEquals(0.3D, scale.valueAt(3));
        assertEquals(0.7D, scale.valueAt(7));
    }

    @Test
    void textHasTheStepsDecimalPlaces() {
        assertEquals("1.50", new DoubleSliderScale(0.5D, 4D, 0.25D).format(4));
        assertEquals("2.0", new DoubleSliderScale(0D, 5D, 0.5D).format(4));
        assertEquals("0.3", new DoubleSliderScale(0D, 1D, 0.1D).format(3));
        assertEquals("3", new DoubleSliderScale(0D, 10D, 1D).format(3));
    }

    @Test
    void textUsesTheMinimumsDecimalPlacesIfItHasMore() {
        // 0.05, 0.15, ...: one decimal place would show 0.1 and 0.2
        var scale = new DoubleSliderScale(0.05D, 1.05D, 0.1D);

        assertEquals("0.05", scale.format(0));
        assertEquals("0.15", scale.format(1));
    }

    @Test
    void textAlwaysUsesAPoint() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1.50", new DoubleSliderScale(0.5D, 4D, 0.25D).format(4));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void indexOfFindsTheNearestPosition() {
        var scale = new DoubleSliderScale(0D, 1D, 0.1D);

        assertEquals(3, scale.indexOf(0.3D));
        assertEquals(4, scale.indexOf(0.37D));
        assertEquals(3, scale.indexOf(0.34D));
        assertEquals(0, scale.indexOf(-5D), "below the range");
        assertEquals(10, scale.indexOf(5D), "above the range");
        assertEquals(10, scale.indexOf(Double.POSITIVE_INFINITY));
        assertEquals(0, scale.indexOf(Double.NaN));
    }

    @Test
    void isPositionOnlyForExactPositions() {
        var scale = new DoubleSliderScale(0.5D, 4D, 0.25D);

        assertTrue(scale.isPosition(0.5D));
        assertTrue(scale.isPosition(1.75D));
        assertTrue(scale.isPosition(4D));
        assertFalse(scale.isPosition(1.3D));
        assertFalse(scale.isPosition(0.25D), "on the grid, but below the range");
        assertFalse(scale.isPosition(4.25D), "on the grid, but above the range");
        assertFalse(scale.isPosition(Double.NaN));
    }

    @Test
    void invalidScalesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(1D, 1D, 0.1D), "min not below max");
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(2D, 1D, 0.1D), "min above max");
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(0D, 1D, 0D), "zero step");
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(0D, 1D, -0.1D), "negative step");
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(0D, 1D, Double.NaN), "NaN step");
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(0D, 1D, 0.3D), "doesn't divide the range");
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(0D, 1D, 1D / 3D), "too many decimal places");
        assertThrows(IllegalArgumentException.class, () -> new DoubleSliderScale(0D, 1000D, 0.001D), "too many positions");
    }
}
