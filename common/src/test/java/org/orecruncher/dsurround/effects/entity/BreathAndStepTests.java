package org.orecruncher.dsurround.effects.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.entity.BreathEffect.Breath;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for when breath is shown, and when an entity counts as moving through brush.
 */
public class BreathAndStepTests {

    private static final int AIR = 300;

    @BeforeAll
    static void setup() {
        // BreathEffect refers to vanilla particle types; loading them unbootstrapped breaks the registries for
        // every later test in the run
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private int visibilityChecks;
    private int coldChecks;

    private BooleanSupplier visible(boolean result) {
        return () -> {
            this.visibilityChecks++;
            return result;
        };
    }

    private BooleanSupplier cold(boolean result) {
        return () -> {
            this.coldChecks++;
            return result;
        };
    }

    // ---- Under water ---------------------------------------------------------------------------------------------

    @Test
    void aBubbleEveryThirdTick() {
        assertEquals(Breath.BUBBLE, BreathEffect.breathFor(3, true, AIR, cold(true), visible(true)));
        assertEquals(Breath.NONE, BreathEffect.breathFor(4, true, AIR, cold(true), visible(true)));
        assertEquals(Breath.NONE, BreathEffect.breathFor(5, true, AIR, cold(true), visible(true)));
    }

    @Test
    void outOfAirIsABurst() {
        assertEquals(Breath.DROWNING, BreathEffect.breathFor(4, true, 0, cold(true), visible(true)));
    }

    @Test
    void belowZeroAirShowsNothing() {
        // Air runs down to -20 before the drowning damage resets it to 0
        assertEquals(Breath.NONE, BreathEffect.breathFor(3, true, -5, cold(true), visible(true)));
    }

    @Test
    void temperatureDoesNotMatterUnderWater() {
        assertEquals(Breath.BUBBLE, BreathEffect.breathFor(0, true, AIR, cold(false), visible(true)));
        assertEquals(0, this.coldChecks);
    }

    // ---- In the air ----------------------------------------------------------------------------------------------

    @Test
    void frostForThirtyTicksOutOfEighty() {
        int frost = 0;
        for (int c = 0; c < 80; c++) {
            if (BreathEffect.breathFor(c, false, AIR, cold(true), visible(true)) == Breath.FROST)
                frost++;
        }
        assertEquals(30, frost);
    }

    @Test
    void noFrostInWarmAir() {
        assertEquals(Breath.NONE, BreathEffect.breathFor(0, false, AIR, cold(false), visible(true)));
    }

    @Test
    void negativeTickCountsStillCycle() {
        // The seeded count is an int cast from a long, so it can be negative
        assertEquals(Breath.FROST, BreathEffect.breathFor(-75, false, AIR, cold(true), visible(true)));
        assertEquals(Breath.BUBBLE, BreathEffect.breathFor(-3, true, AIR, cold(true), visible(true)));
    }

    // ---- Visibility --------------------------------------------------------------------------------------------

    @Test
    void hiddenEntitiesShowNothing() {
        assertEquals(Breath.NONE, BreathEffect.breathFor(0, false, AIR, cold(true), visible(false)));
        assertEquals(Breath.NONE, BreathEffect.breathFor(0, true, 0, cold(true), visible(false)));
    }

    @Test
    void visibilityIsOnlyCheckedWhenThereIsSomethingToShow() {
        // Regression: the line of sight ray cast ran for every entity every tick, before anything else
        BreathEffect.breathFor(0, false, AIR, cold(false), visible(true));      // warm air
        BreathEffect.breathFor(30, false, AIR, cold(true), visible(true));      // outside the frost window
        BreathEffect.breathFor(4, true, AIR, cold(true), visible(true));        // between bubbles
        assertEquals(0, this.visibilityChecks);

        BreathEffect.breathFor(0, false, AIR, cold(true), visible(true));
        assertEquals(1, this.visibilityChecks);
    }

    @Test
    void temperatureIsOnlyCheckedInTheFrostWindow() {
        BreathEffect.breathFor(30, false, AIR, cold(true), visible(true));

        assertEquals(0, this.coldChecks);
    }

    // ---- Brush steps -------------------------------------------------------------------------------------------

    @Test
    void movingThroughBrush() {
        // Regression: movement input was checked, which is only known for the local player
        assertTrue(StepThroughBrushEffect.isMoving(0.2D, 0D, false));
        assertTrue(StepThroughBrushEffect.isMoving(0D, -0.1D, false));
    }

    @Test
    void standingStill() {
        assertFalse(StepThroughBrushEffect.isMoving(0D, 0D, false));
        assertFalse(StepThroughBrushEffect.isMoving(0.0005D, 0.0005D, false), "interpolation jitter isn't movement");
    }

    @Test
    void jumpingInPlace() {
        assertTrue(StepThroughBrushEffect.isMoving(0D, 0D, true));
    }
}
