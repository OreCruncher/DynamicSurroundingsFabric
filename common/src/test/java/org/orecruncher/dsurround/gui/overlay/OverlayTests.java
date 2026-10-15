package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.CompassStyle;
import org.orecruncher.dsurround.config.MarginPosition;
import org.orecruncher.dsurround.lib.gui.ColorGradient;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the overlay logic that doesn't need rendering: which compass spins, the compass wobble, the clock
 * color, the pocket watch's hands and discs, and the diagnostics modes.
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

    // ---- Compass styles --------------------------------------------------------------------------------------

    @Test
    void everyCompassStyleHasItsTexture() {
        for (var style : CompassStyle.values()) {
            if (!style.isShown())
                continue;
            var texture = style.getTexture();
            var path = "/assets/" + texture.getNamespace() + "/" + texture.getPath();
            assertNotNull(OverlayTests.class.getResource(path), style + ": " + path);
        }
    }

    @Test
    void roseTurnsTheHeadingToTheTop() {
        // Turning clockwise on screen moves the top of the texture (north) to the right
        assertEquals(180F, Math.abs(CompassOverlay.roseAngle(0F)), 0.001F, "facing south: north at the bottom");
        assertEquals(0F, CompassOverlay.roseAngle(180F), 0.001F, "facing north: north at the top");
        assertEquals(-90F, CompassOverlay.roseAngle(-90F), 0.001F, "facing east: north to the left");
        assertEquals(90F, CompassOverlay.roseAngle(90F), 0.001F, "facing west: north to the right");
        assertEquals(CompassOverlay.roseAngle(90F), CompassOverlay.roseAngle(90F + 720F), 0.001F, "whole turns");
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

    // ---- Pocket watch ----------------------------------------------------------------------------------------

    @Test
    void pocketWatchHasItsTexture() {
        var texture = ClockOverlay.TEXTURE;
        var path = "/assets/" + texture.getNamespace() + "/" + texture.getPath();
        assertNotNull(OverlayTests.class.getResource(path), path);
    }

    @Test
    void handsShowTheTimeOfDay() {
        // Day time 0 is 6 AM, and an hour is 1000 ticks
        assertEquals(180F, ClockOverlay.hourHandAngle(0), 0.001F, "6 AM: hour hand at six");
        assertEquals(0F, ClockOverlay.minuteHandAngle(0), 0.001F, "on the hour");
        assertEquals(0F, ClockOverlay.hourHandAngle(6000), 0.001F, "noon");
        assertEquals(0F, ClockOverlay.hourHandAngle(18000), 0.001F, "midnight");
        assertEquals(135F, ClockOverlay.hourHandAngle(22500), 0.001F, "4:30 AM: halfway from four to five");
        assertEquals(180F, ClockOverlay.minuteHandAngle(22500), 0.001F, "half past");
        assertEquals(ClockOverlay.hourHandAngle(1234), ClockOverlay.hourHandAngle(1234 + 24000 * 5), 0.001F, "later days");
    }

    @Test
    void handsTickFromMinuteToMinute() {
        for (double time = 0; time < 2000; time += 0.37) {
            var ticked = ClockOverlay.tickedTime(time);
            assertTrue(ticked <= time && time - ticked < ClockOverlay.GAME_MINUTE, "time " + time);
            float minute = ClockOverlay.minuteHandAngle(ticked);
            float mark = Math.round(minute / 6F) * 6F;
            assertEquals(mark, minute, 0.01F, "on a minute mark at " + time);
        }
        assertEquals(6F, ClockOverlay.minuteHandAngle(ClockOverlay.tickedTime(ClockOverlay.GAME_MINUTE * 1.5)), 0.01F, "one minute in");
    }

    @Test
    void minuteHandOvershootsAndSettles() {
        double tick = ClockOverlay.GAME_MINUTE * 30;
        assertEquals(ClockOverlay.TICK_OVERSHOOT, ClockOverlay.tickOvershoot(tick), 0.01F, "just ticked: past the mark");
        assertTrue(ClockOverlay.tickOvershoot(tick + 0.5) < ClockOverlay.tickOvershoot(tick + 0.1), "settling");
        assertEquals(0F, ClockOverlay.tickOvershoot(tick + ClockOverlay.GAME_MINUTE * 0.5), "settled by mid minute");
    }

    @Test
    void dayDiscTellsNoonFromMidnight() {
        // The hands are the same at both; the dome window isn't
        assertEquals(ClockOverlay.hourHandAngle(6000), ClockOverlay.hourHandAngle(18000), 0.001F);
        assertEquals(0F, ClockOverlay.dayDiscAngle(6000), 0.001F, "noon: the disc's top (the sun) up in the window");
        assertEquals(180F, ClockOverlay.dayDiscAngle(18000), 0.001F, "midnight: the moon");
        assertEquals(270F, ClockOverlay.dayDiscAngle(0), 0.001F, "6 AM: three quarters around, the sun rising");
        assertEquals(ClockOverlay.dayDiscAngle(500), ClockOverlay.dayDiscAngle(500 + 24000 * 3), 0.001F, "later days");
    }

    @Test
    void moonDiscBringsEachPhaseToTheWindow() {
        assertEquals(0F, ClockOverlay.moonDiscAngle(0), 0.001F, "full moon");
        assertEquals(180F, ClockOverlay.moonDiscAngle(4), 0.001F, "new moon");
        assertEquals(315F, ClockOverlay.moonDiscAngle(7), 0.001F, "waxing gibbous");
        assertEquals(ClockOverlay.moonDiscAngle(1), ClockOverlay.moonDiscAngle(9), 0.001F, "phases repeat");
    }

    @Test
    void seasonDiscCentersTheSeasonMidSeason() {
        assertEquals(0F, ClockOverlay.seasonDiscAngle(0, 0), 0.001F, "mid spring");
        assertEquals(90F, ClockOverlay.seasonDiscAngle(1, 0), 0.001F, "mid summer");
        assertEquals(180F, ClockOverlay.seasonDiscAngle(2, 0), 0.001F, "mid autumn");
        assertEquals(270F, ClockOverlay.seasonDiscAngle(3, 0), 0.001F, "mid winter");
        assertEquals(360F - ClockOverlay.SUB_SEASON_TURN, ClockOverlay.seasonDiscAngle(0, -1), 0.001F, "early spring");
        assertEquals(270F + ClockOverlay.SUB_SEASON_TURN, ClockOverlay.seasonDiscAngle(3, 1), 0.001F, "late winter");
    }

    @Test
    void seasonDiscEasesForwardFromLateWinterToEarlySpring() {
        // The short way: forward through 360, not back through summer and autumn
        float angle = ClockOverlay.seasonDiscAngle(3, 1);
        float target = ClockOverlay.seasonDiscAngle(0, -1);
        int ticks = 0;
        while (angle != target && ticks < 1000) {
            float next = ClockOverlay.turnToward(angle, target, ClockOverlay.DISC_TURN_PER_TICK);
            assertTrue(Math.abs(Mth.wrapDegrees(next - angle)) <= ClockOverlay.DISC_TURN_PER_TICK + 0.001F, "step " + angle + " to " + next);
            assertTrue(Mth.wrapDegrees(next - angle) > 0, "forward from " + angle);
            angle = next;
            ticks++;
        }
        assertEquals(target, angle, 0.001F);
        assertEquals(Math.round(ClockOverlay.SUB_SEASON_TURN / ClockOverlay.DISC_TURN_PER_TICK), ticks);
    }

    // ---- Screen margin ---------------------------------------------------------------------------------------

    private static final float W = 640F;
    private static final float H = 360F;
    private static final float GAP = MarginLayout.EDGE_GAP;

    @Test
    void marginDefaultsToTheLeftSide() {
        assertEquals(MarginPosition.LEFT, new Configuration().compassAndClockOptions.marginPosition);
        assertFalse(new Configuration().compassAndClockOptions.showInMargin, "off unless asked for");
    }

    @Test
    void leftSideStacksCompassAboveWatchAgainstTheEdge() {
        var p = MarginLayout.place(MarginPosition.LEFT, W, H, 64F, 64F, 64F, 64F);
        assertEquals(GAP + 32F, p.compassX(), 1e-4);
        assertEquals(GAP + 32F, p.watchX(), 1e-4);
        // Centered vertically as a group, the compass first
        assertEquals(H / 2F, (p.compassY() + p.watchY()) / 2F, 1e-4);
        assertEquals(64F + MarginLayout.ITEM_GAP, p.watchY() - p.compassY(), 1e-4);
    }

    @Test
    void aloneEachIsCenteredOnTheSide() {
        var compass = MarginLayout.place(MarginPosition.RIGHT, W, H, 64F, 64F, 0F, 0F);
        assertEquals(W - GAP - 32F, compass.compassX(), 1e-4);
        assertEquals(H / 2F, compass.compassY(), 1e-4);

        var watch = MarginLayout.place(MarginPosition.RIGHT, W, H, 0F, 0F, 64F, 64F);
        assertEquals(W - GAP - 32F, watch.watchX(), 1e-4);
        assertEquals(H / 2F, watch.watchY(), 1e-4);
    }

    @Test
    void narrowerItemHugsItsEdgeInAColumn() {
        // A band (130 wide) over the watch, on the right: both against the right edge
        var p = MarginLayout.place(MarginPosition.TOP_RIGHT, W, H, 130F, 24F, 64F, 64F);
        assertEquals(W - GAP - 65F, p.compassX(), 1e-4);
        assertEquals(W - GAP - 32F, p.watchX(), 1e-4);
        assertEquals(GAP + 12F, p.compassY(), 1e-4);
    }

    @Test
    void topAndBottomCenterSitSideBySide() {
        var top = MarginLayout.place(MarginPosition.TOP, W, H, 64F, 64F, 64F, 64F);
        assertEquals(GAP + 32F, top.compassY(), 1e-4);
        assertEquals(top.compassY(), top.watchY(), 1e-4);
        assertEquals(W / 2F, (top.compassX() + top.watchX()) / 2F, 1e-4);
        assertTrue(top.compassX() < top.watchX(), "compass first");

        // Clear of the hot bar
        var bottom = MarginLayout.place(MarginPosition.BOTTOM, W, H, 64F, 64F, 64F, 64F);
        assertEquals(H - MarginLayout.HOT_BAR_CLEARANCE - 32F, bottom.compassY(), 1e-4);
    }

    @Test
    void everyPositionKeepsBothOnScreen() {
        for (var position : MarginPosition.values()) {
            var p = MarginLayout.place(position, W, H, 64F, 64F, 64F, 64F);
            for (var c : new float[][]{{p.compassX(), p.compassY()}, {p.watchX(), p.watchY()}}) {
                assertTrue(c[0] - 32F >= 0F && c[0] + 32F <= W, position + " x " + c[0]);
                assertTrue(c[1] - 32F >= 0F && c[1] + 32F <= H, position + " y " + c[1]);
            }
        }
    }

    @Test
    void bottomRightReachClearsTheCompassAndWatch() {
        // The light level legend goes above them when they share its corner
        var layout = new MarginLayout();
        layout.setCompass(64F, 48F);
        layout.setWatch(32F, 32F);
        float reach = layout.bottomRightReach(MarginPosition.BOTTOM_RIGHT);
        assertEquals(MarginLayout.EDGE_GAP + 48F + MarginLayout.ITEM_GAP + 32F, reach, 1e-4);

        // Where place puts the top of the pair is that far up
        var p = MarginLayout.place(MarginPosition.BOTTOM_RIGHT, W, H, 64F, 48F, 32F, 32F);
        assertEquals(H - reach, p.compassY() - 24F, 1e-4);
    }

    @Test
    void bottomRightReachIsNothingElsewhereOrWhenHidden() {
        var layout = new MarginLayout();
        layout.setCompass(64F, 48F);
        assertEquals(0F, layout.bottomRightReach(MarginPosition.BOTTOM_LEFT));
        assertEquals(0F, layout.bottomRightReach(MarginPosition.RIGHT));

        layout.setCompass(0F, 0F);
        assertEquals(0F, layout.bottomRightReach(MarginPosition.BOTTOM_RIGHT), "not in the margin");

        layout.setWatch(32F, 32F);
        assertEquals(MarginLayout.EDGE_GAP + 32F, layout.bottomRightReach(MarginPosition.BOTTOM_RIGHT), 1e-4, "the watch alone");
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
