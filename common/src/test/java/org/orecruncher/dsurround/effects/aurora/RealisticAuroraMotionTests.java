package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the realistic aurora's waves, curls and rays move on smoothly, at the speeds given, and stay precise.
 */
public class RealisticAuroraMotionTests {

    // RAY_WRAP comes from the renderer, whose shader takes its id from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final RealisticAurora AURORA = RealisticAurora.create(11, 10);

    private static float distance(float a, float b, double wrap) {
        double d = Math.abs(a - b) % wrap;
        return (float) Math.min(d, wrap - d);
    }

    @Test
    void theyMoveAtTheirSpeeds() {
        var motion = new RealisticAuroraMotion();
        var arc = AURORA.arcs().getFirst();
        motion.advance(AURORA, 1F, 100F);
        float wave = motion.wave(0);
        float ray = motion.ray(0);
        motion.advance(AURORA, 2F, 100.1F);
        assertEquals(Math.abs(arc.waveSpeed()) * 2F * 0.1F, distance(wave, motion.wave(0), arc.waveWavelength()), 1e-3F);
        assertEquals(Math.abs(arc.raySpeed()) * 2F * 0.1F, distance(ray, motion.ray(0), RealisticAuroraMotion.RAY_WRAP), 1e-3F);
    }

    @Test
    void aChangeOfSpeedDoesNotMakeThemLeap() {
        // Late in a long session, when the time is large: only the time since the last frame matters
        var motion = new RealisticAuroraMotion();
        motion.advance(AURORA, 1F, 40000F);
        float ray = motion.ray(0);
        motion.advance(AURORA, 6F, 40000.0625F);
        float most = Math.abs(AURORA.arcs().getFirst().raySpeed()) * 6F * 0.0625F;
        assertTrue(distance(ray, motion.ray(0), RealisticAuroraMotion.RAY_WRAP) <= most + 1e-3F);
    }

    @Test
    void aPauseDoesNotMakeThemLeap() {
        var motion = new RealisticAuroraMotion();
        motion.advance(AURORA, 1F, 10F);
        float ray = motion.ray(0);
        motion.advance(AURORA, 1F, 70F);
        float most = Math.abs(AURORA.arcs().getFirst().raySpeed()) * RealisticAuroraMotion.MAX_STEP;
        assertTrue(distance(ray, motion.ray(0), RealisticAuroraMotion.RAY_WRAP) <= most + 1e-3F);
    }

    @Test
    void theyStayWithinTheirWraps() {
        var motion = new RealisticAuroraMotion();
        for (int frame = 0; frame < 20000; frame++) {
            motion.advance(AURORA, 6F, frame * 0.25F);
            for (int i = 0; i < AURORA.arcs().size(); i++) {
                var arc = AURORA.arcs().get(i);
                assertTrue(motion.wave(i) >= 0F && motion.wave(i) <= arc.waveWavelength());
                assertTrue(motion.curl(i) >= 0F && motion.curl(i) <= RealisticAurora.CURL_WAVELENGTH);
                assertTrue(motion.ray(i) >= 0F && motion.ray(i) <= RealisticAuroraMotion.RAY_WRAP);
            }
        }
    }

    @Test
    void anotherAuroraStartsOver() {
        var motion = new RealisticAuroraMotion();
        motion.advance(AURORA, 1F, 0F);
        motion.advance(AURORA, 1F, 0.2F);
        var other = RealisticAurora.create(12, 10);
        motion.advance(other, 1F, 0.4F);
        assertEquals(0F, motion.wave(0));
        assertEquals(0F, motion.curl(0));
    }

    @Test
    void aChangeOfActivityCarriesOn() {
        // /dsaurora makes it more or less active with the same arcs: they shouldn't jump back to their start
        var motion = new RealisticAuroraMotion();
        motion.advance(AURORA, 1F, 0F);
        motion.advance(AURORA, 1F, 0.2F);
        float wave = motion.wave(0);
        motion.advance(AURORA.withActivity(AuroraActivity.STORM), 1F, 0.2F);
        assertEquals(wave, motion.wave(0));
    }

    @Test
    void theRayFieldDrifts() {
        var motion = new RealisticAuroraMotion();
        motion.advance(AURORA, 1F, 0F);
        motion.advance(AURORA, 2F, 0.125F);
        assertEquals(RealisticAuroraMotion.FIELD_SPEED * 2F * 0.125F, motion.field(), 1e-4F);
        for (int frame = 0; frame < 20000; frame++) {
            motion.advance(AURORA, 6F, 0.125F + frame * 0.25F);
            assertTrue(motion.field() >= 0F && motion.field() <= RealisticAuroraMotion.FIELD_WRAP);
        }
    }

    @Test
    void theRaysRepeatWhereTheyWrap() {
        // A whole number of both ray noises' repeats, so the wrap can't be seen
        double fine = RealisticAuroraRenderer.NOISE_SIZE * (double) RealisticAuroraRenderer.FINE_RAY;
        double coarse = RealisticAuroraRenderer.NOISE_SIZE * (double) RealisticAuroraRenderer.COARSE_RAY;
        double fineRepeats = RealisticAuroraMotion.RAY_WRAP / fine;
        double coarseRepeats = RealisticAuroraMotion.RAY_WRAP / coarse;
        assertEquals(Math.round(fineRepeats), fineRepeats, 1e-3);
        assertEquals(Math.round(coarseRepeats), coarseRepeats, 1e-3);
    }
}
