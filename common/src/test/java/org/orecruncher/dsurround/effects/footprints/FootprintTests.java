package org.orecruncher.dsurround.effects.footprints;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.FootprintStyle;
import org.orecruncher.dsurround.effects.footprints.StrideTracker.Step;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for footprints: when feet come down, how a print is laid out and where in the texture, how prints age and
 * fade, and how they fit the body that leaves them.
 */
public class FootprintTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- Strides ---------------------------------------------------------------------------------------------

    @Test
    void aFootComesDownEachStrideTakingTurns() {
        var tracker = new StrideTracker();
        int steps = 0;
        boolean lastFoot = false;
        for (int tick = 0; tick < 100; tick++) {
            // Walking: about 0.22 blocks a tick
            var step = tracker.update(0.22D, 0D, true, 1.25D);
            if (step == Step.ONE) {
                if (steps > 0)
                    assertNotEquals(lastFoot, tracker.rightFoot(), "feet take turns");
                lastFoot = tracker.rightFoot();
                steps++;
            } else {
                assertEquals(Step.NONE, step);
            }
        }
        // 22 blocks at 1.25 a step
        assertEquals(17, steps);
    }

    @Test
    void standingStillLeavesNothing() {
        var tracker = new StrideTracker();
        for (int tick = 0; tick < 100; tick++)
            assertEquals(Step.NONE, tracker.update(0D, 0D, true, 1.25D));
    }

    @Test
    void aFastTickPutsDownOneFootNotSeveral() {
        var tracker = new StrideTracker();
        assertEquals(Step.ONE, tracker.update(5D, 0D, true, 1.25D));
        // What is carried over is at most a stride
        assertEquals(Step.ONE, tracker.update(0D, 0D, true, 1.25D));
        assertEquals(Step.NONE, tracker.update(0D, 0D, true, 1.25D));
    }

    @Test
    void landingPutsDownBothFeet() {
        var tracker = new StrideTracker();
        for (int tick = 0; tick < StrideTracker.AIRBORNE_TICKS; tick++)
            assertEquals(Step.NONE, tracker.update(0.2D, 0D, false, 1.25D));
        assertEquals(Step.BOTH, tracker.update(0.2D, 0D, true, 1.25D));
        // And walking starts over from there
        assertEquals(Step.NONE, tracker.update(0.2D, 0D, true, 1.25D));
    }

    @Test
    void steppingUpABlockIsNotALanding() {
        var tracker = new StrideTracker();
        tracker.update(0.2D, 0D, false, 1.25D);
        tracker.update(0.2D, 0D, false, 1.25D);
        assertNotEquals(Step.BOTH, tracker.update(0.2D, 0D, true, 1.25D));
    }

    // ---- Layout ----------------------------------------------------------------------------------------------

    @Test
    void aPrintFacesTheWayTheBodyDoes() {
        float[] c = new float[8];
        // Facing south (+Z): the front corners are to the south, the right ones to the west (-X)
        Footprints.layOut(c, 0F, 0.1F, 0.2F);
        assertEquals(0.2F, c[1], 1.0E-5F, "front left is in front");
        assertEquals(-0.2F, c[3], 1.0E-5F, "back left is behind");
        assertEquals(0.1F, c[0], 1.0E-5F, "left of facing south is east");
        assertEquals(-0.1F, c[4], 1.0E-5F, "right of facing south is west");
        // Facing north (180): mirrored
        Footprints.layOut(c, 180F, 0.1F, 0.2F);
        assertEquals(-0.2F, c[1], 1.0E-5F);
        assertEquals(-0.1F, c[0], 1.0E-5F);
    }

    @Test
    void cornersWindTheSameWayWhateverTheFacing() {
        float[] c = new float[8];
        for (float yaw = -180F; yaw < 180F; yaw += 15F) {
            Footprints.layOut(c, yaw, 0.1F, 0.2F);
            // Signed area in the x-z plane: the same sign for every facing
            double area = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                area += c[2 * i] * c[2 * j + 1] - c[2 * j] * c[2 * i + 1];
            }
            assertTrue(area < 0, "winding flipped at yaw " + yaw);
            assertEquals(4 * 0.1 * 0.2, Math.abs(area) / 2, 1.0E-5, "area at yaw " + yaw);
        }
    }

    @Test
    void eachStyleHasItsOwnPairInTheTexture() {
        float lastEnd = 0F;
        for (var style : FootprintStyle.values()) {
            float leftStart = style.u0(false), leftEnd = style.u1(false);
            float rightStart = style.u0(true), rightEnd = style.u1(true);
            assertTrue(leftStart >= lastEnd && leftStart < leftEnd && leftEnd < rightStart && rightStart < rightEnd);
            lastEnd = rightEnd;
        }
        assertTrue(lastEnd <= 1F);
        assertEquals(1F / 8F, FootprintStyle.SQUARE.u0(false), 0.01F);
    }

    // ---- Aging -----------------------------------------------------------------------------------------------

    @Test
    void rainWashesOnlyWhatItFallsOn() {
        assertEquals(1F, Footprints.agingRate(0F, 1F));
        assertEquals(1F, Footprints.agingRate(1F, 0F));
        assertEquals(1F + Footprints.RAIN_AGING, Footprints.agingRate(1F, 1F));
    }

    @Test
    void printsFadeFasterTowardTheEnd() {
        assertEquals(Footprints.PEAK_ALPHA, Footprints.alphaAt(0F));
        float early = Footprints.PEAK_ALPHA - Footprints.alphaAt(0.25F);
        float late = Footprints.alphaAt(0.75F) - Footprints.alphaAt(1F);
        assertTrue(late > early);
        assertEquals(0F, Footprints.alphaAt(1F));
        assertEquals(0F, Footprints.alphaAt(2F));
    }

    @Test
    void snowKeepsPrintsLonger() {
        assertTrue(Footprints.SNOW_LIFE > Footprints.LIFE * 3);
    }

    // ---- Fit to the body -------------------------------------------------------------------------------------

    @Test
    void aPlayersStrideSpreadAndSize() {
        assertEquals(1.25D, FootprintEffect.strideFor(1.8F), 1.0E-6D);
        assertEquals(0.2D, FootprintEffect.spreadFor(0.6F), 1.0E-6D);
        assertEquals(1F, FootprintEffect.scaleFor(0.6F), 1.0E-6F);
    }

    @Test
    void smallerBodiesLeaveSmallerCloserPrints() {
        // A chicken (0.4 by 0.7) against a cow (0.9 by 1.4)
        assertTrue(FootprintEffect.strideFor(0.7F) < FootprintEffect.strideFor(1.4F));
        assertTrue(FootprintEffect.spreadFor(0.4F) < FootprintEffect.spreadFor(0.9F));
        assertTrue(FootprintEffect.scaleFor(0.4F) < FootprintEffect.scaleFor(0.9F));
        // Kept within bounds for the very small and very large
        assertEquals(0.5D, FootprintEffect.strideFor(0.1F), 1.0E-6D);
        assertEquals(1.3F, FootprintEffect.scaleFor(4F), 1.0E-6F);
    }
}
