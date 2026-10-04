package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.gui.ColorGradient;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the overlay logic that doesn't need rendering: which compass spins, the compass wobble, the clock
 * color and the diagnostics modes.
 */
public class OverlayTests {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- Compass spin ----------------------------------------------------------------------------------------

    @Test
    void wobblingCompassInEitherHandSpins() {
        assertTrue(CompassOverlay.shouldSpin(true, true, false, false), "main hand");
        assertTrue(CompassOverlay.shouldSpin(false, false, true, true), "off hand");
        assertTrue(CompassOverlay.shouldSpin(true, false, true, true), "normal in main, wobbling in off");
    }

    @Test
    void normalCompassDoesNotSpin() {
        assertFalse(CompassOverlay.shouldSpin(true, false, false, false));
        assertFalse(CompassOverlay.shouldSpin(false, false, true, false));
        assertFalse(CompassOverlay.shouldSpin(false, false, false, false), "no compass at all");
    }

    @Test
    void spinIsDecidedFromWhatIsHeldNow() {
        // Regression: switching from a wobbling compass in the main hand to a normal one in the off hand kept
        // spinning, because nothing reset the earlier answer
        assertTrue(CompassOverlay.shouldSpin(true, true, false, false), "before: wobbling compass in the main hand");
        assertFalse(CompassOverlay.shouldSpin(false, false, true, false), "after: normal compass in the off hand");
    }

    // ---- Compass wobble --------------------------------------------------------------------------------------

    @Test
    void wobbleUpdatesOncePerTick() {
        var wobble = new CompassOverlay.CompassWobble();
        for (long tick = 1; tick <= 200; tick++)
            wobble.update(tick);
        float before = wobble.getRandomlySpinningRotation(1F);

        wobble.update(200);   // the same tick again

        assertEquals(before, wobble.getRandomlySpinningRotation(1F));
    }

    @Test
    void wobbleMovesInSmallStepsWithinATurn() {
        var wobble = new CompassOverlay.CompassWobble();
        float previous = wobble.getRandomlySpinningRotation(1F);
        boolean moved = false;

        for (long tick = 1; tick <= 2_000; tick++) {
            wobble.update(tick);
            float rotation = wobble.getRandomlySpinningRotation(1F);
            assertTrue(rotation >= 0F && rotation <= 360F, "rotation " + rotation);
            assertTrue(Math.abs(rotation - previous) <= CompassOverlay.CompassWobble.MAX_DELTA_TICK * 360F + 1.0E-3F,
                    "step from " + previous + " to " + rotation);
            moved |= rotation != previous;
            previous = rotation;
        }

        assertTrue(moved, "it picks new targets and moves towards them");
    }

    // ---- Clock color -----------------------------------------------------------------------------------------

    private static final ColorGradient GRADIENT = new ColorGradient(ColorPalette.DARK_VIOLET, ColorPalette.SUN_GLOW, 180F);

    @Test
    void clockColorIsAlwaysOpaque() {
        // Regression: the color had no alpha; newer versions draw that invisibly
        for (int i = 0; i <= 100; i++) {
            int color = ClockOverlay.textColor(GRADIENT, i / 100F);
            assertEquals(0xFF, color >>> 24, "time of day " + i / 100F);
        }
    }

    @Test
    void clockColorRunsFromMidnightToNoon() {
        // Time of day 0 is noon and 0.5 is midnight
        assertEquals(0xFF000000 | ColorPalette.SUN_GLOW.getValue(), ClockOverlay.textColor(GRADIENT, 0F), "noon");
        assertEquals(0xFF000000 | ColorPalette.DARK_VIOLET.getValue(), ClockOverlay.textColor(GRADIENT, 0.5F), "midnight");
        assertEquals(ClockOverlay.textColor(GRADIENT, 0.25F), ClockOverlay.textColor(GRADIENT, 0.75F),
                "dusk and dawn are the same point on the gradient");
    }

    // ---- Diagnostics modes -----------------------------------------------------------------------------------

    @Test
    void diagnosticsModesCycle() {
        assertEquals(DiagnosticsOverlay.Mode.DEBUG, DiagnosticsOverlay.Mode.OFF.next());
        assertEquals(DiagnosticsOverlay.Mode.BIOME, DiagnosticsOverlay.Mode.DEBUG.next());
        assertEquals(DiagnosticsOverlay.Mode.EFFECTS, DiagnosticsOverlay.Mode.BIOME.next());
        assertEquals(DiagnosticsOverlay.Mode.OFF, DiagnosticsOverlay.Mode.EFFECTS.next());
    }
}
