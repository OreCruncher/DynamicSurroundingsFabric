package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the realistic aurora: each night's arcs are where real ones would be seen, its night unfolds in order and
 * smoothly, and its shaders agree with the mod on what they share.
 */
public class RealisticAuroraTests {

    // The renderer's shader takes its id from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int NIGHTS = 2000;
    private static final int STORM_CHANCE = 10;

    private static RealisticAurora create(long night) {
        return RealisticAurora.create(night, STORM_CHANCE);
    }

    private static String resource(String path) throws IOException {
        try (var in = Objects.requireNonNull(RealisticAuroraTests.class.getResourceAsStream("/assets/dsurround/shaders/" + path), path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static float constant(String source, String pattern) {
        var match = Pattern.compile(pattern).matcher(source);
        assertTrue(match.find(), pattern);
        return Float.parseFloat(match.group(1));
    }

    // ---- The nights

    @Test
    void aNightAlwaysHasTheSameAurora() {
        for (long night = 0; night < 50; night++)
            assertEquals(create(night), create(night));
    }

    @Test
    void nightsDiffer() {
        assertNotEquals(create(1), create(2));
    }

    @Test
    void thereIsRoomInTheShaderForEveryArc() {
        for (long night = 0; night < NIGHTS; night++) {
            var arcs = create(night).arcs();
            assertFalse(arcs.isEmpty());
            assertTrue(arcs.size() <= RealisticAurora.MAX_ARCS);
        }
    }

    @Test
    void theNearestArcStartsInTheNorthernSky() {
        // Its lower edge in the north, from low over the horizon to well up the sky, whenever, across the part of the sky
        // in front of the viewer: its ends, bowed, may come further up the sky or go further down
        for (long night = 0; night < NIGHTS; night++) {
            var arc = create(night).arcs().getFirst();
            for (float time = 0F; time < 600F; time += 97F) {
                for (float along = -200F; along <= 200F; along += 20F) {
                    float north = arc.northAt(along, time, 0F, time * arc.waveSpeed(), time * arc.raySpeed());
                    assertTrue(north > 0F, "night " + night + ": south of the viewer");
                    float elevation = RealisticAurora.elevation(north, RealisticAurora.LOWER_EDGE);
                    assertTrue(elevation > 8F && elevation < 36F, "night " + night + ": lower edge at " + elevation + " degrees");
                }
            }
        }
    }

    @Test
    void anArcsBowBendsItsEndsMost() {
        for (long night = 0; night < 500; night++) {
            for (var arc : create(night).arcs()) {
                assertTrue(Math.abs(arc.bow()) <= RealisticAurora.MAX_BOW);
                assertEquals(0F, arc.bowAt(0F), 1e-6F);
                // Half way at BOW_LENGTH, and never past its full bend
                assertEquals(arc.bow() * 0.5F, arc.bowAt(RealisticAurora.BOW_LENGTH), 1e-3F);
                assertTrue(Math.abs(arc.bowAt(1e6F)) <= Math.abs(arc.bow()));
                assertTrue(Math.abs(arc.bowAt(1000F)) > Math.abs(arc.bowAt(200F)) || arc.bow() == 0F);
            }
        }
    }

    @Test
    void arcsBowBothWays() {
        boolean north = false;
        boolean south = false;
        for (long night = 0; night < 200; night++) {
            float bow = create(night).arcs().getFirst().bow();
            north |= bow > 50F;
            south |= bow < -50F;
        }
        assertTrue(north && south);
    }

    @Test
    void fartherArcsAreLowerAndFainter() {
        for (long night = 0; night < NIGHTS; night++) {
            var arcs = create(night).arcs();
            for (int i = 1; i < arcs.size(); i++) {
                assertTrue(arcs.get(i).distance() > arcs.get(i - 1).distance(), "night " + night + ": not nearest first");
                assertTrue(arcs.get(i).brightness() < arcs.get(i - 1).brightness(), "night " + night + ": not fainter");
            }
            // The farthest still clear of the horizon, wherever along it
            var farthest = arcs.getLast();
            assertTrue(farthest.distance() <= RealisticAurora.FARTHEST);
            float elevation = RealisticAurora.elevation(farthest.distance() + farthest.reach(), RealisticAurora.LOWER_EDGE);
            assertTrue(elevation > 3F, "night " + night + ": farthest at " + elevation + " degrees");
        }
    }

    @Test
    void nightsHaveFromOneToFourArcs() {
        var seen = new boolean[RealisticAurora.MAX_ARCS + 1];
        for (long night = 0; night < NIGHTS; night++)
            seen[create(night).arcs().size()] = true;
        for (int count = 1; count <= RealisticAurora.MAX_ARCS; count++)
            assertTrue(seen[count], "never " + count);
    }

    @Test
    void anArcsShapeIsUsable() {
        for (long night = 0; night < NIGHTS; night++) {
            for (var arc : create(night).arcs()) {
                // The shader divides by these, and steps along lines of sight far coarser than a fold could be
                assertTrue(arc.thickness() > 0F);
                assertTrue(arc.foldWavelength() >= 200F);
                assertTrue(arc.waveWavelength() >= 50F);
                assertTrue(arc.brightness() > 0F);
                // Curls thinner than the sheet is thick, so a curl doesn't fold it back over itself
                assertTrue(arc.curlAmplitude() < arc.thickness());
                // Bent gently enough that, seen slantwise, its rays don't zig-zag
                assertTrue(arc.steepest() <= RealisticAurora.MAX_SLOPE + 1e-4F, "night " + night + ": slope " + arc.steepest());
            }
        }
    }

    @Test
    void anArcStaysWithinItsReach() {
        for (long night = 0; night < 200; night++) {
            for (var arc : create(night).arcs()) {
                for (float along = -1000F; along <= 1000F; along += 7F) {
                    float off = Math.abs(arc.northAt(along, along * 0.3F, 0F, along * 0.7F, along * 1.3F) - arc.distance() - arc.bowAt(along));
                    assertTrue(off <= arc.reach() + 1e-3F, "night " + night + ": " + off + " off, reach " + arc.reach());
                }
            }
        }
    }

    // ---- Which way magnetic north is, and its colours

    @Test
    void magneticNorthIsNearTrueNorth() {
        float least = Float.MAX_VALUE;
        float most = -Float.MAX_VALUE;
        for (long night = 0; night < NIGHTS; night++) {
            float declination = create(night).declination();
            assertTrue(Math.abs(declination) <= RealisticAurora.MAX_DECLINATION);
            least = Math.min(least, declination);
            most = Math.max(most, declination);
        }
        // Both ways, by a good part of the most
        assertTrue(least < -RealisticAurora.MAX_DECLINATION * 0.8F && most > RealisticAurora.MAX_DECLINATION * 0.8F);
    }

    @Test
    void onlyStormsAreCrimson() {
        for (float roll = 0F; roll < 1F; roll += 0.01F) {
            for (var activity : AuroraActivity.values()) {
                if (activity != AuroraActivity.STORM)
                    assertNotEquals(AuroraColour.CRIMSON, AuroraColour.of(roll, activity));
            }
        }
    }

    @Test
    void stormsHaveEveryColour() {
        var seen = EnumSet.noneOf(AuroraColour.class);
        for (float roll = 0F; roll < 1F; roll += 0.01F)
            seen.add(AuroraColour.of(roll, AuroraActivity.STORM));
        assertEquals(EnumSet.allOf(AuroraColour.class), seen);
    }

    @Test
    void mostNightsHaveUsualColours() {
        int usual = 0;
        for (long night = 0; night < NIGHTS; night++)
            if (RealisticAurora.create(night, 0).colour() == AuroraColour.NORMAL)
                usual++;
        assertTrue(usual > NIGHTS * 0.6F && usual < NIGHTS * 0.85F, usual + " of " + NIGHTS);
    }

    @Test
    void theColourFollowsTheActivity() {
        // The same roll, read for how active it now is
        for (long night = 0; night < 200; night++) {
            var aurora = create(night);
            var storm = aurora.withActivity(AuroraActivity.STORM);
            assertEquals(AuroraColour.of(aurora.colourRoll(), AuroraActivity.STORM), storm.colour());
        }
    }

    @Test
    void aColourChangesItsLight() {
        var aurora = create(9).withActivity(AuroraActivity.ACTIVE);
        float p = aurora.progressOf(RealisticAurora.Phase.EXPANSION);
        var usual = aurora.withColour(AuroraColour.NORMAL).at(p);
        var crimson = aurora.withColour(AuroraColour.CRIMSON).at(p);
        assertEquals(usual.red() * AuroraColour.CRIMSON.red(), crimson.red(), 1e-5F);
        assertEquals(usual.fringe() * AuroraColour.CRIMSON.fringe(), crimson.fringe(), 1e-5F);
        assertEquals(AuroraColour.CRIMSON.green(), crimson.green());
        assertEquals(1F, usual.green());
    }

    // ---- The Earth's shadow

    @Test
    void thereIsNoShadowWhileTheSunIsUp() {
        for (float angle = -90F; angle <= 90F; angle += 5F)
            assertEquals(0F, RealisticAurora.shadowHeight(angle), "at " + angle);
        assertEquals(0F, RealisticAurora.shadowHeight(300F));
    }

    @Test
    void theShadowRisesAsTheSunSinks() {
        // 10 and 18 degrees down, at dusk and at dawn
        assertEquals(98F, RealisticAurora.shadowHeight(100F), 2F);
        assertEquals(327F, RealisticAurora.shadowHeight(108F), 3F);
        assertEquals(RealisticAurora.shadowHeight(100F), RealisticAurora.shadowHeight(260F), 1e-3F);
        float last = 0F;
        for (float angle = 90F; angle <= 180F; angle += 1F) {
            float height = RealisticAurora.shadowHeight(angle);
            assertTrue(height >= last);
            last = height;
        }
        assertEquals(RealisticAurora.MAX_SHADOW, RealisticAurora.shadowHeight(180F));
    }

    // ---- The moon

    @Test
    void aFullMoonHighUpWashesOutMost() {
        assertEquals(RealisticAurora.MOON_WASHOUT, RealisticAurora.moonWashout(180F, 1F), 1e-6F);
        assertEquals(RealisticAurora.MOON_WASHOUT * 0.5F, RealisticAurora.moonWashout(180F, 0.5F), 1e-6F);
    }

    @Test
    void noMoonWashesOutNothing() {
        // New, or down (while the sun is up, the moon is down)
        assertEquals(0F, RealisticAurora.moonWashout(180F, 0F));
        for (float angle = -90F; angle <= 90F; angle += 5F)
            assertEquals(0F, RealisticAurora.moonWashout(angle, 1F), 1e-6F, "at " + angle);
    }

    @Test
    void aLowMoonWashesOutLess() {
        // Rising: 10 degrees up, then 30
        float low = RealisticAurora.moonWashout(100F, 1F);
        float higher = RealisticAurora.moonWashout(120F, 1F);
        assertTrue(low > 0F && low < higher, low + " then " + higher);
        assertEquals(RealisticAurora.MOON_WASHOUT, higher, 1e-6F);
    }

    // ---- How active a night is

    @Test
    void theStormChanceDecidesStorms() {
        for (long night = 0; night < 500; night++) {
            assertNotEquals(AuroraActivity.STORM, RealisticAurora.activityOf(night, 0));
            assertEquals(AuroraActivity.STORM, RealisticAurora.activityOf(night, 100));
        }
    }

    @Test
    void everyActivityHappens() {
        var seen = EnumSet.noneOf(AuroraActivity.class);
        for (long night = 0; night < NIGHTS; night++)
            seen.add(create(night).activity());
        assertEquals(EnumSet.allOf(AuroraActivity.class), seen);
    }

    @Test
    void theStormChanceDoesNotChangeTheArcs() {
        // The same arcs, but for a storm's second, when the night would otherwise have had only one
        for (long night = 0; night < 200; night++) {
            var calm = RealisticAurora.create(night, 0).arcs();
            var stormy = RealisticAurora.create(night, 100).arcs();
            if (calm.size() >= 2)
                assertEquals(calm, stormy, "night " + night);
            else
                assertEquals(calm.getFirst(), stormy.getFirst(), "night " + night);
        }
    }

    @Test
    void aStormHasAtLeastTwoArcs() {
        for (long night = 0; night < 500; night++)
            assertTrue(RealisticAurora.create(night, 100).arcs().size() >= 2);
    }

    // ---- Its night

    @Test
    void aQuietNightOnlyDriftsCloser() {
        for (long night = 0; night < 200; night++) {
            var aurora = create(night).withActivity(AuroraActivity.QUIET);
            for (float p = 0F; p <= 1F; p += 0.01F) {
                var moment = aurora.at(p);
                assertEquals(RealisticAurora.Phase.GROWTH, moment.phase());
                assertEquals(0F, moment.surge());
                assertEquals(0F, moment.pulse());
            }
            assertEquals(RealisticAurora.GROWTH_DRIFT, aurora.at(1F).approach(), 1e-4F);
        }
    }

    @Test
    void anActiveNightGoesThroughItsPhasesInOrder() {
        for (long night = 0; night < 200; night++) {
            for (var activity : new AuroraActivity[]{AuroraActivity.MODERATE, AuroraActivity.ACTIVE, AuroraActivity.STORM}) {
                var aurora = create(night).withActivity(activity);
                var seen = EnumSet.noneOf(RealisticAurora.Phase.class);
                var last = RealisticAurora.Phase.GROWTH;
                for (float p = 0F; p <= 1F; p += 0.002F) {
                    var phase = aurora.at(p).phase();
                    assertTrue(phase.ordinal() >= last.ordinal(), "night " + night + ": " + phase + " after " + last);
                    last = phase;
                    seen.add(phase);
                }
                assertEquals(EnumSet.allOf(RealisticAurora.Phase.class), seen, "night " + night + ", " + activity);
            }
        }
    }

    @Test
    void aNightChangesSmoothly() {
        // No jumps from one moment to the next, a thousandth of the night apart (about half a second): the breakup is
        // the quickest change, over BREAKUP_LENGTH
        float step = 0.001F;
        for (long night = 0; night < 100; night++) {
            for (var activity : AuroraActivity.values()) {
                var aurora = create(night).withActivity(activity);
                var before = aurora.at(0F);
                for (float p = step; p <= 1F; p += step) {
                    var moment = aurora.at(p);
                    assertEquals(before.surge(), moment.surge(), 0.06F, "surge at " + p);
                    assertEquals(before.approach(), moment.approach(), 25F, "approach at " + p);
                    assertEquals(before.gain(), moment.gain(), 0.25F, "gain at " + p);
                    assertEquals(before.pulse(), moment.pulse(), 0.02F, "pulse at " + p);
                    assertEquals(before.rayContrast(), moment.rayContrast(), 0.03F, "rays at " + p);
                    before = moment;
                }
            }
        }
    }

    @Test
    void aStormComesOverhead() {
        for (long night = 0; night < 200; night++) {
            var aurora = create(night).withActivity(AuroraActivity.STORM);
            var moment = aurora.at(aurora.progressOf(RealisticAurora.Phase.EXPANSION));
            float nearest = aurora.arcs().getFirst().distance() - moment.approach();
            assertEquals(AuroraActivity.STORM.nearestAtPeak(), nearest, 1e-3F, "night " + night);
        }
    }

    @Test
    void aModerateBreakupStaysInTheNorth() {
        for (long night = 0; night < 200; night++) {
            var aurora = create(night).withActivity(AuroraActivity.MODERATE);
            var moment = aurora.at(aurora.progressOf(RealisticAurora.Phase.EXPANSION));
            // As near as a moderate breakup brings it, or as near as it had drifted already, if that is nearer
            float start = aurora.arcs().getFirst().distance();
            float least = Math.min(AuroraActivity.MODERATE.nearestAtPeak(), start - RealisticAurora.GROWTH_DRIFT);
            float nearest = start - moment.approach();
            assertTrue(nearest >= least - 1e-3F, "night " + night + ": " + nearest + " km");
        }
    }

    @Test
    void holdingAPhaseLandsInIt() {
        for (long night = 0; night < 200; night++) {
            for (var activity : new AuroraActivity[]{AuroraActivity.MODERATE, AuroraActivity.ACTIVE, AuroraActivity.STORM}) {
                var aurora = create(night).withActivity(activity);
                for (var phase : RealisticAurora.Phase.values())
                    assertEquals(phase, aurora.at(aurora.progressOf(phase)).phase(), "night " + night + ", " + activity);
            }
        }
    }

    @Test
    void itPulsesOnlyAsItFades() {
        for (long night = 0; night < 100; night++) {
            var aurora = create(night).withActivity(AuroraActivity.ACTIVE);
            for (float p = 0F; p <= 1F; p += 0.01F) {
                var moment = aurora.at(p);
                if (moment.phase() != RealisticAurora.Phase.RECOVERY)
                    assertEquals(0F, moment.pulse());
                assertTrue(moment.rayContrast() >= 0F && moment.rayContrast() <= 1F);
            }
            assertTrue(aurora.at(1F).pulse() > 0F || aurora.peakEnd() >= 1F);
        }
    }

    @Test
    void onlyActiveNightsAndStormsHaveARayField() {
        for (long night = 0; night < 100; night++) {
            for (var activity : AuroraActivity.values()) {
                var aurora = create(night).withActivity(activity);
                float strongest = 0F;
                for (float p = 0F; p <= 1F; p += 0.01F)
                    strongest = Math.max(strongest, aurora.at(p).rayField());
                if (activity == AuroraActivity.ACTIVE || activity == AuroraActivity.STORM)
                    assertTrue(strongest > 0F, "night " + night + ", " + activity);
                else
                    assertEquals(0F, strongest, "night " + night + ", " + activity);
            }
        }
    }

    @Test
    void aStormsRayFieldIsOverhead() {
        // So the rays radiate from above the viewer
        for (long night = 0; night < 200; night++) {
            var aurora = create(night).withActivity(AuroraActivity.STORM);
            var moment = aurora.at(aurora.progressOf(RealisticAurora.Phase.EXPANSION));
            assertTrue(moment.fieldNorth() - moment.fieldWidth() * 0.5F < 0F && moment.fieldNorth() + moment.fieldWidth() * 0.5F > 0F,
                    "night " + night + ": " + moment.fieldNorth() + " km, " + moment.fieldWidth() + " either way");
        }
    }

    @Test
    void theRayFieldComesAndGoesWithTheSurge() {
        var aurora = create(3).withActivity(AuroraActivity.STORM);
        assertEquals(0F, aurora.at(aurora.progressOf(RealisticAurora.Phase.GROWTH)).rayField());
        assertEquals(AuroraActivity.STORM.rayField(), aurora.at(aurora.progressOf(RealisticAurora.Phase.EXPANSION)).rayField(), 1e-5F);
        assertTrue(aurora.at(aurora.progressOf(RealisticAurora.Phase.RECOVERY)).rayField() < AuroraActivity.STORM.rayField());
    }

    @Test
    void aChangeOfActivityKeepsTheArcs() {
        var aurora = create(5);
        var storm = aurora.withActivity(AuroraActivity.STORM);
        assertSame(aurora.arcs(), storm.arcs());
        assertEquals(aurora.onset(), storm.onset());
        assertEquals(AuroraActivity.STORM, storm.activity());
    }

    // ---- How high things look

    @Test
    void overheadIsStraightUp() {
        assertEquals(90F, RealisticAurora.elevation(0F, 100F), 1e-3F);
    }

    @Test
    void nearbyItLooksAsOverFlatGround() {
        assertEquals(Math.toDegrees(Math.atan2(100, 100)), RealisticAurora.elevation(100F, 100F), 1F);
    }

    @Test
    void farOffTheEarthsCurveLowersIt() {
        // 1000 km off, 100 km up: about 5.7 degrees over flat ground, the curve takes off about 4.5
        float flat = (float) Math.toDegrees(Math.atan2(100, 1000));
        float seen = RealisticAurora.elevation(1000F, 100F);
        assertTrue(seen < flat - 4F && seen > 0F, seen + " degrees");
    }

    // ---- What the shaders share with the mod

    @Test
    void theShaderAgreesOnItsConstants() throws IOException {
        var fragment = resource("core/aurora_realistic.fsh");
        assertEquals(RealisticAuroraRenderer.NOISE_SIZE, constant(fragment, "const float NOISE_SIZE = ([0-9.]+);"));
        assertEquals(RealisticAuroraRenderer.FINE_RAY, constant(fragment, "const float FINE_RAY = ([0-9.]+);"));
        assertEquals(RealisticAuroraRenderer.COARSE_RAY, constant(fragment, "const float COARSE_RAY = ([0-9.]+);"));
        assertEquals(RealisticAuroraRenderer.FIELD_CELL, constant(fragment, "const float FIELD_CELL = ([0-9.]+);"));
        assertEquals(RealisticAurora.BOW_LENGTH, constant(fragment, "const float BOW_LENGTH = ([0-9.]+);"));
        assertEquals(RealisticAurora.EARTH_RADIUS, constant(fragment, "const float EARTH_RADIUS = ([0-9.]+);"));
        assertEquals(RealisticAurora.FOLD_DRIFT, constant(fragment, "const float FOLD_DRIFT = ([0-9.]+);"));
        assertEquals(RealisticAurora.CURL_WAVELENGTH, constant(fragment, "const float CURL_WAVELENGTH = ([0-9.]+);"));
        assertEquals(RealisticAurora.MAX_ARCS, constant(resource("include/aurora_realistic.glsl"), "#define MAX_ARCS ([0-9]+)"));
    }

    @Test
    void theShaderBendsArcsAsTheModDoes() throws IOException {
        // Arc.northAt, in the shader
        var fragment = resource("core/aurora_realistic.fsh");
        assertTrue(fragment.contains("shape.x - shape.y * sin(east * TWO_PI / shape.z + shape.w + time * FOLD_DRIFT)"), "large folds");
        assertTrue(fragment.contains("wave.x * sin((east - wave.z) * TWO_PI / wave.y)"), "the wave");
        assertTrue(fragment.contains("wave.w * sin((east - ArcLight[i].z) * TWO_PI / CURL_WAVELENGTH + shape.w * 3.0)"), "curls");
        assertTrue(fragment.contains("return north + arc_bow(i, east);"), "the bow");
        assertTrue(fragment.contains("ArcBow[i].x * e2 / (e2 + BOW_LENGTH * BOW_LENGTH)"), "the bow's shape");
    }
}
