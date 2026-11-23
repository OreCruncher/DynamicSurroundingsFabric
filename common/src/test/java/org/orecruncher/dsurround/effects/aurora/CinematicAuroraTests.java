// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.ShaderSources;

import java.io.IOException;
import java.util.EnumSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the cinematic aurora: its nights, its motion, the values its shader is given, and that the shader's
 * constants and formulas are the Java model's. No GPU needed.
 */
public class CinematicAuroraTests {

    // The renderer's shader takes its id from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int NIGHTS = 2000;

    private static RealisticAurora.Moment moment(long night, RealisticAurora.Phase phase) {
        var aurora = RealisticAurora.create(night, 10).withActivity(AuroraActivity.STORM);
        return aurora.at(aurora.progressOf(phase));
    }

    // ---- The nights

    @Test
    void aNightAlwaysHasTheSameAurora() {
        for (long night = 0; night < 50; night++)
            assertEquals(CinematicAurora.create(night), CinematicAurora.create(night));
    }

    @Test
    void nightsDiffer() {
        assertNotEquals(CinematicAurora.create(1), CinematicAurora.create(2));
    }

    @Test
    void everyNightHasRibbonsTheShaderHasRoomFor() {
        var layouts = EnumSet.noneOf(CinematicAurora.Layout.class);
        for (long night = 0; night < NIGHTS; night++) {
            var aurora = CinematicAurora.create(night);
            layouts.add(aurora.layout());
            int count = aurora.ribbons().size();
            assertTrue(count >= 1 && count <= CinematicAurora.MAX_RIBBONS, "night " + night + ": " + count);
            if (aurora.layout() == CinematicAurora.Layout.WAVES)
                assertTrue(count >= 2, "night " + night + ": waves of one");
        }
        assertEquals(EnumSet.allOf(CinematicAurora.Layout.class), layouts, "both layouts come up");
    }

    @Test
    void ribbonsBendNoSteeperThanTheShaderAllows() {
        for (long night = 0; night < NIGHTS; night++)
            for (var ribbon : CinematicAurora.create(night).ribbons())
                assertTrue(ribbon.steepest() <= CinematicAurora.MAX_SLOPE + 1e-4F, "night " + night + ": " + ribbon.steepest());
    }

    @Test
    void ribbonsHaveWhatTheShaderDividesBy() {
        for (long night = 0; night < NIGHTS; night++)
            for (var ribbon : CinematicAurora.create(night).ribbons()) {
                assertTrue(ribbon.foldWavelength() > 0F && ribbon.waveWavelength() > 0F, "night " + night);
                assertTrue(ribbon.thickness() > 0F && ribbon.height() > 0F, "night " + night);
                assertTrue(ribbon.raySharpness() >= 0F && ribbon.raySharpness() <= 1F, "night " + night);
            }
    }

    @Test
    void theFirstRibbonIsTheBrightest() {
        for (long night = 0; night < NIGHTS; night++) {
            var ribbons = CinematicAurora.create(night).ribbons();
            for (int i = 1; i < ribbons.size(); i++)
                assertTrue(ribbons.get(i).brightness() < ribbons.getFirst().brightness(), "night " + night);
        }
    }

    @Test
    void aHorseshoePassesNearOverheadWithItsEndsBentFarBack() {
        int seen = 0;
        for (long night = 0; night < NIGHTS; night++) {
            var aurora = CinematicAurora.create(night);
            if (aurora.layout() != CinematicAurora.Layout.HORSESHOE)
                continue;
            seen++;
            var first = aurora.ribbons().getFirst();
            assertTrue(Math.abs(first.distance()) <= 100F, "night " + night);
            assertTrue(Math.abs(first.bow()) >= 350F, "night " + night);
            // Bent away from the viewer's side, so it curves back over the sky rather than across the viewer
            assertTrue(first.distance() == 0F || Math.signum(first.bow()) == Math.signum(first.distance()), "night " + night);
        }
        assertTrue(seen > 0);
    }

    @Test
    void aBreakupDrawsTheRibbonsInAndLowersTheirEdge() {
        var ribbon = CinematicAurora.create(7).ribbons().getFirst();
        assertEquals(ribbon.distance(), CinematicAurora.distanceAt(ribbon, 0F), 1e-6F);
        assertTrue(Math.abs(CinematicAurora.distanceAt(ribbon, 1F)) <= Math.abs(ribbon.distance()));
        assertEquals(CinematicAurora.LOWER_EDGE, CinematicAurora.lowerEdgeAt(0F), 1e-6F);
        assertTrue(CinematicAurora.lowerEdgeAt(1F) < CinematicAurora.LOWER_EDGE);
    }

    @Test
    void theBowIsNothingOverheadAndItsFullBendFarOff() {
        var ribbon = CinematicAurora.create(3).ribbons().getFirst();
        assertEquals(0F, ribbon.bowAt(0F), 1e-6F);
        assertEquals(ribbon.bow() * 0.5F, ribbon.bowAt(CinematicAurora.BOW_LENGTH), 1e-3F);
        assertEquals(ribbon.bow(), ribbon.bowAt(1e6F), Math.abs(ribbon.bow()) * 1e-3F + 1e-3F);
    }

    // ---- Motion

    @Test
    void motionStaysWithinItsRepeats() {
        var aurora = CinematicAurora.create(11);
        var motion = new CinematicAuroraMotion();
        for (int frame = 0; frame < 20000; frame++)
            motion.advance(aurora, 5F, frame * 0.05F);
        for (int i = 0; i < aurora.ribbons().size(); i++) {
            var ribbon = aurora.ribbons().get(i);
            assertTrue(motion.fold(i) >= 0F && motion.fold(i) < (float) (Math.PI * 2), "fold " + i);
            assertTrue(motion.wave(i) >= 0F && motion.wave(i) < ribbon.waveWavelength(), "wave " + i);
            assertTrue(motion.ray(i) >= 0F && motion.ray(i) < CinematicAuroraMotion.RAY_WRAP, "ray " + i);
        }
    }

    @Test
    void aChangeOfSpeedDoesntMakeThemLeap() {
        var aurora = CinematicAurora.create(5);
        var motion = new CinematicAuroraMotion();
        motion.advance(aurora, 1F, 1000F);
        motion.advance(aurora, 1F, 1000.05F);
        float before = motion.ray(0);
        // Five times faster from here: one frame moves it on only five frames' worth
        motion.advance(aurora, 5F, 1000.1F);
        var ribbon = aurora.ribbons().getFirst();
        float moved = Math.abs(motion.ray(0) - before);
        assertTrue(moved <= Math.abs(ribbon.raySpeed()) * 5F * 0.05F + 1e-3F, "moved " + moved);
    }

    @Test
    void anotherNightStartsItsMotionOver() {
        var motion = new CinematicAuroraMotion();
        var first = CinematicAurora.create(1);
        motion.advance(first, 1F, 0F);
        motion.advance(first, 1F, 0.25F);
        var second = CinematicAurora.create(2);
        motion.advance(second, 1F, 500F);
        assertEquals(0F, motion.wave(0), 1e-6F);
    }

    @Test
    void theRaysRepeatWhereBothNoisesDo() {
        double fine = CinematicAuroraRenderer.NOISE_SIZE * (double) CinematicAuroraRenderer.FINE_RAY;
        double coarse = CinematicAuroraRenderer.NOISE_SIZE * (double) CinematicAuroraRenderer.COARSE_RAY;
        double wrap = CinematicAuroraMotion.RAY_WRAP;
        assertEquals(0D, Math.IEEEremainder(wrap, fine), 1e-3, "a whole number of the fine noise's repeats");
        assertEquals(0D, Math.IEEEremainder(wrap, coarse), 1e-3, "a whole number of the coarse noise's repeats");
    }

    // ---- The frame's values

    @Test
    void theBlockIsFull() {
        var aurora = CinematicAurora.create(9);
        var frame = CinematicAuroraFrame.of(aurora, moment(9, RealisticAurora.Phase.GROWTH), new CinematicAuroraMotion(), 0.5F,
                new Matrix4f().perspective(1.2F, 1.6F, 0.05F, 1000F), new Matrix4f(), 10F, 1080);
        assertEquals(CinematicAuroraFrame.VECTORS * 4, frame.vec4s().length);
        assertEquals(aurora.ribbons().size(), frame.vec4s()[CinematicAuroraFrame.RIBBON_COUNT], 1e-6F);
        assertEquals(0.5F, frame.vec4s()[CinematicAuroraFrame.ALPHA], 1e-6F);
        for (float value : frame.vec4s())
            assertTrue(Float.isFinite(value));
    }

    @Test
    void itsStrengthIsHeldToOne() {
        var frame = CinematicAuroraFrame.of(CinematicAurora.create(9), moment(9, RealisticAurora.Phase.GROWTH), new CinematicAuroraMotion(), 3F,
                new Matrix4f().perspective(1.2F, 1.6F, 0.05F, 1000F), new Matrix4f(), 10F, 1080);
        assertEquals(1F, frame.vec4s()[CinematicAuroraFrame.ALPHA], 1e-6F);
    }

    @Test
    void thePixelAngleIsTheScreensHeightOverTheView() {
        var projection = new Matrix4f().perspective(1.2F, 1.6F, 0.05F, 1000F);
        var frame = CinematicAuroraFrame.of(CinematicAurora.create(9), moment(9, RealisticAurora.Phase.GROWTH), new CinematicAuroraMotion(), 1F,
                projection, new Matrix4f(), 10F, 1000);
        assertEquals(2F / (projection.m11() * 1000), frame.vec4s()[CinematicAuroraFrame.PIXEL_ANGLE], 1e-7F);
    }

    @Test
    void aBreakupShowsMoreViolet() {
        var aurora = CinematicAurora.create(9);
        var projection = new Matrix4f().perspective(1.2F, 1.6F, 0.05F, 1000F);
        var calm = CinematicAuroraFrame.of(aurora, moment(9, RealisticAurora.Phase.GROWTH), new CinematicAuroraMotion(), 1F,
                projection, new Matrix4f(), 10F, 1080).vec4s();
        var peak = CinematicAuroraFrame.of(aurora, moment(9, RealisticAurora.Phase.EXPANSION), new CinematicAuroraMotion(), 1F,
                projection, new Matrix4f(), 10F, 1080).vec4s();
        assertTrue(peak[CinematicAuroraFrame.VIOLET] > calm[CinematicAuroraFrame.VIOLET]);
    }

    @Test
    void ribbonsThereArentDrawNothing() {
        // Find a night with fewer ribbons than there is room for
        CinematicAurora aurora = null;
        for (long night = 0; aurora == null; night++) {
            var candidate = CinematicAurora.create(night);
            if (candidate.ribbons().size() < CinematicAurora.MAX_RIBBONS)
                aurora = candidate;
        }
        var v = CinematicAuroraFrame.of(aurora, moment(aurora.night(), RealisticAurora.Phase.GROWTH), new CinematicAuroraMotion(), 1F,
                new Matrix4f().perspective(1.2F, 1.6F, 0.05F, 1000F), new Matrix4f(), 10F, 1080).vec4s();
        int max = CinematicAurora.MAX_RIBBONS;
        int header = CinematicAuroraFrame.HEADER;
        for (int i = aurora.ribbons().size(); i < max; i++) {
            int axis = (header + i) * 4;
            int fold = (header + max + i) * 4;
            int wave = (header + 2 * max + i) * 4;
            int light = (header + 3 * max + i) * 4;
            assertEquals(0F, v[light], "no brightness");
            // A unit axis, and nothing of zero for the shader to divide by
            assertEquals(1F, v[axis] * v[axis] + v[axis + 1] * v[axis + 1], 1e-6F);
            assertTrue(v[fold + 1] > 0F && v[wave] > 0F && v[wave + 3] > 0F && v[light + 1] > 0F);
        }
    }

    @Test
    void eachRibbonsAxisIsAUnitVectorAlongItsHeading() {
        var aurora = CinematicAurora.create(21);
        var v = CinematicAuroraFrame.of(aurora, moment(21, RealisticAurora.Phase.GROWTH), new CinematicAuroraMotion(), 1F,
                new Matrix4f().perspective(1.2F, 1.6F, 0.05F, 1000F), new Matrix4f(), 10F, 1080).vec4s();
        for (int i = 0; i < aurora.ribbons().size(); i++) {
            int axis = CinematicAuroraFrame.RIBBON_AXES + i * 4;
            float heading = aurora.ribbons().get(i).heading();
            assertEquals(Math.cos(heading), v[axis], 1e-6F);
            assertEquals(Math.sin(heading), v[axis + 1], 1e-6F);
        }
    }

    // ---- The shader is the model's

    @Test
    void theShadersConstantsAreTheModels() throws IOException {
        var fragment = ShaderSources.read("core/aurora_cinematic.fsh");
        assertEquals(CinematicAuroraRenderer.NOISE_SIZE, ShaderSources.constant(fragment, "const float NOISE_SIZE = ([0-9.]+);"));
        assertEquals(CinematicAuroraRenderer.FINE_RAY, ShaderSources.constant(fragment, "const float FINE_RAY = ([0-9.]+);"));
        assertEquals(CinematicAuroraRenderer.COARSE_RAY, ShaderSources.constant(fragment, "const float COARSE_RAY = ([0-9.]+);"));
        assertEquals(CinematicAurora.BOW_LENGTH, ShaderSources.constant(fragment, "const float BOW_LENGTH = ([0-9.]+);"));
        assertEquals(CinematicAurora.CURL_STRETCH, ShaderSources.constant(fragment, "const float CURL_STRETCH = ([0-9.]+);"));
        assertEquals(RealisticAurora.EARTH_RADIUS, ShaderSources.constant(fragment, "const float EARTH_RADIUS = ([0-9.]+);"));
        assertEquals(CinematicAurora.MAX_RIBBONS, ShaderSources.constant(ShaderSources.read("include/aurora_cinematic.glsl"), "#define MAX_RIBBONS ([0-9]+)"));
    }

    @Test
    void theShadersRibbonIsTheModels() throws IOException {
        var fragment = ShaderSources.read("core/aurora_cinematic.fsh");
        assertTrue(fragment.contains("fold.x * sin(along * TWO_PI / fold.y + fold.z)"), "large folds");
        assertTrue(fragment.contains("fold.w * sin((along - wave.y) * TWO_PI / wave.x)"), "the wave");
        assertTrue(fragment.contains("RibbonAxis[i].w * a2 / (a2 + BOW_LENGTH * BOW_LENGTH)"), "the bow");
        assertTrue(fragment.contains("float along = dot(r, axis) / CURL_STRETCH;"), "the twist's stretch");
        assertTrue(fragment.contains("float q = (along * along + across * across) / (curl.z * curl.z);"), "the twist's distance");
        assertTrue(fragment.contains("float angle = curl.w * exp(-q);"), "the twist");
        assertTrue(fragment.contains("vec2(r.x * c - r.y * s, r.x * s + r.y * c)"), "the twist's turn");
        assertTrue(fragment.contains("RibbonCurl[i].z * CURL_STRETCH * min(0.43 * abs(RibbonCurl[i].w), 2.6)"), "the curl's reach");
    }

    @Test
    void theBlockHasTheFramesVectorsInOrder() throws IOException {
        var include = ShaderSources.read("include/aurora_cinematic.glsl");
        var single = Pattern.compile("^\\s*vec4 (\\w+);", Pattern.MULTILINE).matcher(include);
        int singles = 0;
        while (single.find())
            singles++;
        assertEquals(CinematicAuroraFrame.HEADER, singles, "vectors before the ribbons'");
        var arrays = Pattern.compile("^\\s*vec4 (\\w+)\\[MAX_RIBBONS\\];", Pattern.MULTILINE).matcher(include);
        var names = new java.util.ArrayList<String>();
        while (arrays.find())
            names.add(arrays.group(1));
        assertEquals(java.util.List.of("RibbonAxis", "RibbonFold", "RibbonWave", "RibbonLight", "RibbonCurl"), names);
    }

    // ---- Curls

    @Test
    void mostNightsHaveACurlAndSomeRibbonsDont() {
        int first = 0;
        int plain = 0;
        for (long night = 0; night < NIGHTS; night++) {
            var ribbons = CinematicAurora.create(night).ribbons();
            if (ribbons.getFirst().curl().twist() != 0F)
                first++;
            for (var ribbon : ribbons)
                if (ribbon.curl() == CinematicAurora.Curl.NONE)
                    plain++;
        }
        assertTrue(first > NIGHTS * 0.7 && first < NIGHTS * 0.9, "first ribbons curled: " + first);
        assertTrue(plain > 0);
    }

    @Test
    void aTwistTurnsMostAtItsMiddleAndLeavesFarPointsAlone() {
        // A point just off the middle turns by nearly the whole twist
        var near = CinematicAurora.twist(1F, 0F, 0F, 0F, 50F, 2F, 1F, 0F);
        assertEquals(2F, (float) Math.atan2(near[1], near[0]), 1e-3F);
        // Far out it stays where it is
        var far = CinematicAurora.twist(800F, 30F, 0F, 0F, 50F, 2F, 1F, 0F);
        assertEquals(800F, far[0], 1e-3F);
        assertEquals(30F, far[1], 1e-3F);
    }

    @Test
    void aTwistReachesFartherAlongTheRibbonThanAcrossIt() {
        // The ribbon runs along z here: a point 100 km along it turns more than one 100 km across it
        float twist = 2F;
        var along = CinematicAurora.twist(0F, 100F, 0F, 0F, 50F, twist, 0F, 1F);
        var across = CinematicAurora.twist(100F, 0F, 0F, 0F, 50F, twist, 0F, 1F);
        float turnedAlong = Math.abs((float) Math.atan2(along[1], along[0]) - (float) Math.PI / 2F);
        float turnedAcross = Math.abs((float) Math.atan2(across[1], across[0]));
        assertTrue(turnedAlong > 3F * turnedAcross, turnedAlong + " along against " + turnedAcross + " across");
    }

    @Test
    void noPointMovesFurtherThanTheReach() {
        var random = org.orecruncher.dsurround.lib.random.Randomizer.create(6);
        for (float twist : new float[]{0.5F, 1F, 2F, 3F, 4F, 5.5F, 8.25F, 12F}) {
            float reach = CinematicAurora.Curl.reachOf(60F, twist);
            for (int n = 0; n < 4000; n++) {
                float x = random.nextFloat(-600F, 600F);
                float z = random.nextFloat(-600F, 600F);
                var moved = CinematicAurora.twist(x, z, 0F, 0F, 60F, twist, 0.6F, 0.8F);
                float distance = (float) Math.hypot(moved[0] - x, moved[1] - z);
                assertTrue(distance <= reach + 1e-3F, "twist " + twist + " moved " + distance + " past " + reach);
            }
        }
    }

    @Test
    void aCurlDoublesTheRibbonBack() {
        // The ribbon seen is where the twisted layer puts a point on its line (along x here). A ribbon that only folds
        // crosses each line across it (x held) once; one that doubles back crosses some three times. The least twist a
        // curl has does, either way, with its middle as far off the line as a curl puts it (a radius)
        for (float twist : new float[]{2F, -2F}) {
            int most = 0;
            for (float x = -300F; x <= 300F; x += 2F) {
                int crossings = 0;
                float before = Float.NaN;
                for (float z = -300F; z <= 300F; z += 0.5F) {
                    float across = CinematicAurora.twist(x, z, 0F, 40F, 40F, twist, 1F, 0F)[1];
                    if (!Float.isNaN(before) && (before < 0F) != (across < 0F))
                        crossings++;
                    before = across;
                }
                most = Math.max(most, crossings);
            }
            assertTrue(most >= 3, "twist " + twist + " crossed a line at most " + most + " times");
        }
    }

    @Test
    void theCurlsMiddleIsOnTheRibbonsLine() {
        for (long night = 0; night < 50; night++) {
            var aurora = CinematicAurora.create(night);
            var motion = new CinematicAuroraMotion();
            motion.advance(aurora, 1F, 0F);
            var moment = moment(night, RealisticAurora.Phase.GROWTH);
            var v = CinematicAuroraFrame.of(aurora, moment, motion, 1F,
                    new Matrix4f().perspective(1.2F, 1.6F, 0.05F, 1000F), new Matrix4f(), 10F, 1080).vec4s();
            int max = CinematicAurora.MAX_RIBBONS;
            for (int i = 0; i < aurora.ribbons().size(); i++) {
                var ribbon = aurora.ribbons().get(i);
                var curl = ribbon.curl();
                int at = (CinematicAuroraFrame.HEADER + 4 * max + i) * 4;
                float x = v[at];
                float z = v[at + 1];
                // Back into the ribbon's frame: along it, and across it to the left
                float cos = (float) Math.cos(ribbon.heading());
                float sin = (float) Math.sin(ribbon.heading());
                float along = x * cos + z * sin;
                float across = -x * sin + z * cos;
                float surge = Math.clamp(moment.surge(), 0F, 1F);
                assertEquals(curl.along(), along, 1e-2F);
                assertEquals(ribbon.centerAt(along, CinematicAurora.distanceAt(ribbon, surge), motion.fold(i), motion.wave(i)) + curl.offset(),
                        across, 1e-2F);
                assertEquals(curl.radius(), v[at + 2], 1e-6F);
                assertEquals(curl.twistAt(motion.curl(i), surge), v[at + 3], 1e-6F);
            }
            // Ribbons there aren't don't twist
            for (int i = aurora.ribbons().size(); i < max; i++)
                assertEquals(0F, v[(CinematicAuroraFrame.HEADER + 4 * max + i) * 4 + 3]);
        }
    }

    @Test
    void activityNarrowsTheGapBetweenOrdinaryNightsAndStorms() {
        // A storm at its height is three times as bright as an ordinary night in the realistic aurora; here less than
        // one and a half times, still brighter, and never negative
        float ordinary = CinematicAuroraFrame.activityGain(1F);
        float storm = CinematicAuroraFrame.activityGain(3F);
        assertEquals(1F, ordinary, 1e-6F);
        assertTrue(storm > ordinary && storm < 1.5F * ordinary, "storm " + storm);
        assertEquals(0F, CinematicAuroraFrame.activityGain(-1F));
    }
}
