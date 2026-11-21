// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Mixers;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.ArrayList;
import java.util.List;

/**
 * One night's cinematic aurora: the stylized ribbons of photographs and games rather than the real thing. Wide, soft
 * curtains of saturated green, pale at their lower edges and violet toward their tops, folding in long S-curves and
 * free to sweep overhead from one horizon to the other. Drawn from the night's number, so everyone in a world sees the
 * same ribbons on the same night.
 * <p>
 * A ribbon is a curtain hanging straight down from a line in a layer of the sky {@link #LOWER_EDGE} kilometers up. The
 * line runs along a {@link Ribbon#heading} (any way at all: these aren't held to magnetic east-west as the realistic
 * arcs are), {@link Ribbon#distance} to the side of the viewer, folded two ways (large folds that sway, a wave that
 * travels) and bowed at its ends. Perspective does the rest: overhead a ribbon is wide, toward the horizon it narrows to
 * a thread.
 * <p>
 * Most ribbons also curl: around a point on or near the line, the layer is turned (a twist, {@link #twist}), most at the
 * point and less with distance from it, farther along the ribbon than across it, so the line bends into a long hook or
 * S, doubling back on itself as photographs show. An offset along an axis alone can't double back. A twist the same
 * every way wound the ribbons into rings, which looked drawn with a compass.
 * <p>
 * How active it is through the night comes from the realistic aurora of the same night ({@link RealisticAurora#at}):
 * quiet nights stay calm, and as a night breaks up the ribbons brighten, draw in toward overhead
 * ({@link #distanceAt}), move faster and show more violet. Its own colors are fixed, one signature palette (in the
 * shader).
 *
 * @param night   the night's number (the day, counted from the world's start)
 * @param layout  how its ribbons are laid out
 * @param ribbons its ribbons, the first brightest
 */
public record CinematicAurora(long night, Layout layout, List<Ribbon> ribbons) {

    /**
     * The most ribbons there can be: the shader has room for this many
     */
    public static final int MAX_RIBBONS = 4;
    /**
     * How high a ribbon's lower edge is, in kilometers, before a breakup lowers it
     */
    public static final float LOWER_EDGE = 100F;
    /**
     * How far along a ribbon, either way, its bow has bent it half as far as it goes, in kilometers (in the shader,
     * BOW_LENGTH)
     */
    public static final float BOW_LENGTH = 400F;
    /**
     * How much of the way in toward overhead the ribbons are drawn at the height of a breakup
     */
    static final float SURGE_DRAW = 0.6F;
    /**
     * How far a breakup lowers the lower edge at its height, in kilometers
     */
    static final float EDGE_DROP = 12F;
    /**
     * The steepest a ribbon's folds together bend it, in kilometers across for each kilometer along. Steeper than the
     * realistic arcs: the S-curves are the point. The shader's offset from the ribbon, worked out square to it, stays
     * close enough at this slope
     */
    static final float MAX_SLOPE = 1.1F;

    /**
     * How likely a ribbon is to curl, in percent: the first (brightest) ribbon, and the others
     */
    static final int FIRST_CURL_CHANCE = 80;
    static final int CURL_CHANCE = 50;
    /**
     * How much more a curl is twisted at the height of a breakup
     */
    static final float SURGE_TWIST = 0.3F;
    /**
     * How many times farther a curl's twist reaches along its ribbon than across it (in the shader, CURL_STRETCH)
     */
    public static final float CURL_STRETCH = 2.5F;
    /**
     * How far a curl's twist breathes, either way, as a share of it
     */
    static final float TWIST_BREATH = 0.3F;

    // Apart from the classic and realistic auroras' shapes, though all come from the night's number
    private static final long SALT = 0x63696E65L;
    // The curls come from a randomizer of their own, so a night's ribbons are the ones they were before curls
    private static final long CURL_SALT = 0x6375726CL;

    /**
     * How a night's ribbons are laid out
     */
    public enum Layout {
        /**
         * Two to four ribbons side by side, a little off parallel, filling the sky in waves
         */
        WAVES,
        /**
         * One or two, the first passing near overhead with its ends bent far back: a great horseshoe over the viewer
         */
        HORSESHOE
    }

    /**
     * One ribbon. Its line, in a frame turned to its heading (along it, and across it to the left), is
     * {@link #centerAt}.
     *
     * @param heading        which way it runs, in radians from the world's +x toward +z
     * @param distance       how far across from the viewer its line is, in kilometers, before a breakup draws it in
     * @param foldAmplitude  how far its large folds reach across, in kilometers
     * @param foldWavelength how far along it from one large fold to the next, in kilometers
     * @param foldPhase      where along their cycle the folds start, in radians
     * @param foldSpeed      how fast the folds sway along their cycle, in radians a second, when calm
     * @param waveAmplitude  how far the traveling wave reaches across, in kilometers
     * @param waveWavelength how far along it from one crest of the wave to the next, in kilometers
     * @param waveSpeed      how fast the wave travels along it, in kilometers a second, when calm
     * @param bow            how far its ends bend away (toward, if negative) from the viewer's side, in kilometers
     * @param thickness      how thick the curtain is, in kilometers: the spread of its soft sheet
     * @param height         how far it rises above its lower edge, in kilometers
     * @param brightness     how bright it is, relative to a typical ribbon
     * @param rayOffset      where in the shader's pattern its rays and patches start, in kilometers, so each differs
     * @param raySpeed       how fast its rays move along it, in kilometers a second, when calm
     * @param raySharpness   how distinct its rays are, 0 (smoky) to 1 (crisp pillars)
     * @param curl           its curl, or {@link Curl#NONE}
     */
    public record Ribbon(float heading, float distance, float foldAmplitude, float foldWavelength, float foldPhase,
                         float foldSpeed, float waveAmplitude, float waveWavelength, float waveSpeed, float bow,
                         float thickness, float height, float brightness, float rayOffset, float raySpeed,
                         float raySharpness, Curl curl) {

        /**
         * How far across its line is, {@code along} kilometers along it (in the shader, ribbon_center)
         *
         * @param distance how far across it is now (see {@link CinematicAurora#distanceAt})
         * @param fold     where along their cycle the folds are, in radians (see {@link CinematicAuroraMotion})
         * @param wave     how far the wave has traveled along it, in kilometers
         */
        public float centerAt(float along, float distance, float fold, float wave) {
            return distance + this.bowAt(along)
                    + this.foldAmplitude * (float) Math.sin(along * Mth.TWO_PI / this.foldWavelength + fold)
                    + this.waveAmplitude * (float) Math.sin((along - wave) * Mth.TWO_PI / this.waveWavelength);
        }

        /**
         * How far its bow bends it {@code along} kilometers along it: hardly at all near the viewer, toward
         * {@link #bow} at its ends (in the shader, ribbon_bow)
         */
        public float bowAt(float along) {
            float a2 = along * along;
            return this.bow * a2 / (a2 + BOW_LENGTH * BOW_LENGTH);
        }

        /**
         * The most its line strays across from where it is, in kilometers, the bow aside
         */
        public float reach() {
            return this.foldAmplitude + this.waveAmplitude;
        }

        /**
         * The steepest its folds together bend it, in kilometers across for each kilometer along
         */
        public float steepest() {
            return Mth.TWO_PI * (this.foldAmplitude / this.foldWavelength + this.waveAmplitude / this.waveWavelength);
        }
    }

    /**
     * A ribbon's curl: where the layer is twisted, and how far
     *
     * @param along  how far along the ribbon from the viewer it is, in kilometers
     * @param offset how far across from the ribbon's line its middle is, in kilometers: 0 winds the line into an S,
     *               further out into a hook to one side
     * @param radius how far out the twist reaches across the ribbon, in kilometers (down to a third of its turn there;
     *               {@link #CURL_STRETCH} times as far along it)
     * @param twist  how far the layer is turned at its middle, in radians, either way
     * @param phase  where its breathing starts, in radians
     * @param speed  how fast its twist breathes, in radians a second, when calm
     */
    public record Curl(float along, float offset, float radius, float twist, float phase, float speed) {
        public static final Curl NONE = new Curl(0F, 0F, 1F, 0F, 0F, 0F);

        /**
         * How far its twist turns the layer now
         *
         * @param breath where its breathing is, in radians (see {@link CinematicAuroraMotion#curl})
         * @param surge  how far into its breakup the night is
         */
        public float twistAt(float breath, float surge) {
            return this.twist * (1F + TWIST_BREATH * (float) Math.sin(breath)) * (1F + SURGE_TWIST * Math.clamp(surge, 0F, 1F));
        }

        /**
         * The farthest a twist moves a point, in kilometers: where to look for the ribbon beyond where its line can be
         * (in the shader, curl_reach). A point r from the middle moves 2 r sin(turn / 2), no more than r times its
         * turn. Its turn is no more than a twist the same every way, reaching as far as the twist does along the ribbon
         * (R'), would give it: r twist exp(-r^2/R'^2), at most 0.43 R' twist; and for a twist up to 12 radians no more
         * than 2.6 R', where the turn is near half a turn (a test checks)
         */
        public static float reachOf(float radius, float twist) {
            return radius * CURL_STRETCH * Math.min(0.43F * Math.abs(twist), 2.6F);
        }
    }

    /**
     * Where a point on the ground is looked for in the untwisted layer: turned about the curl's middle ({@code cx},
     * {@code cz}) by {@code twist} there, less with distance as exp(-q), q the distance squared over the radius squared
     * with the part along the ribbon ({@code ax}, {@code az}, a unit vector) shortened {@link #CURL_STRETCH} times
     * (in the shader, curl_warp)
     *
     * @return the point, x and z
     */
    public static float[] twist(float x, float z, float cx, float cz, float radius, float twist, float ax, float az) {
        float rx = x - cx;
        float rz = z - cz;
        float along = (rx * ax + rz * az) / CURL_STRETCH;
        float across = -rx * az + rz * ax;
        float angle = twist * (float) Math.exp(-(along * along + across * across) / (radius * radius));
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        return new float[]{cx + rx * c - rz * s, cz + rx * s + rz * c};
    }

    /**
     * The aurora for the given night
     */
    public static CinematicAurora create(long night) {
        var random = Randomizer.create(Mixers.splitMix64(night ^ SALT));
        float heading = random.nextFloat() * Mth.TWO_PI;
        var layout = random.nextInt(100) < 35 ? Layout.HORSESHOE : Layout.WAVES;
        int count = layout == Layout.HORSESHOE ? 1 + random.nextInt(2) : 2 + random.nextInt(3);
        // The first ribbon near overhead, the others further out to one side
        float distance = layout == Layout.HORSESHOE ? random.nextFloat(-100F, 100F) : random.nextFloat(-150F, 250F);
        float side = random.nextBoolean() ? 1F : -1F;
        float brightness = random.nextFloat(0.85F, 1.1F);

        var curls = Randomizer.create(Mixers.splitMix64(night ^ CURL_SALT));
        var ribbons = new ArrayList<Ribbon>(count);
        for (int i = 0; i < count; i++) {
            float foldWavelength = random.nextFloat(350F, 800F);
            float waveWavelength = random.nextFloat(120F, 260F);
            // A horseshoe's first ribbon bends its ends far back, away from the side it is on
            float bow = layout == Layout.HORSESHOE && i == 0
                    ? random.nextFloat(350F, 600F) * (distance < 0F ? -1F : 1F)
                    : random.nextFloat(-150F, 150F);
            ribbons.add(new Ribbon(
                    heading + random.nextFloat(-0.2F, 0.2F),
                    distance,
                    amplitude(random.nextFloat(0.3F, 0.75F), foldWavelength),
                    foldWavelength,
                    random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat(0.01F, 0.03F) * sign(random),
                    amplitude(random.nextFloat(0.1F, 0.3F), waveWavelength),
                    waveWavelength,
                    random.nextFloat(1.5F, 5F) * sign(random),
                    bow,
                    random.nextFloat(2F, 4F),
                    random.nextFloat(110F, 220F),
                    brightness,
                    random.nextFloat() * 1000F,
                    random.nextFloat(0.5F, 2.5F) * sign(random),
                    random.nextFloat(0.3F, 1F),
                    curl(curls, i)));
            distance += side * random.nextFloat(70F, 180F);
            brightness *= random.nextFloat(0.6F, 0.9F);
        }
        return new CinematicAurora(night, layout, List.copyOf(ribbons));
    }

    /**
     * A ribbon's curl, if it has one: in the stretch of sky a viewer sees well, a long hook of a third to half a turn
     * (wound up to most of a turn, they read as rings). Small beside the ribbon, and its middle off to one side of the
     * line: one centered on it bends the line into an S whose two arms, seen from below at a slant, closed into an oval,
     * and one up to 110 km across filled the sky with it
     */
    private static Curl curl(IRandomizer random, int index) {
        // Drawn either way, so each ribbon's draws are the same whether or not it curls
        boolean curls = random.nextInt(100) < (index == 0 ? FIRST_CURL_CHANCE : CURL_CHANCE);
        float radius = random.nextFloat(25F, 60F);
        var curl = new Curl(
                random.nextFloat(-350F, 350F),
                random.nextFloat(0.4F, 1F) * sign(random) * radius,
                radius,
                random.nextFloat(2F, 3.2F) * sign(random),
                random.nextFloat() * Mth.TWO_PI,
                random.nextFloat(0.02F, 0.05F));
        return curls ? curl : Curl.NONE;
    }

    /**
     * How far across from the viewer a ribbon's line is at a point in its night: drawn in toward overhead as the night
     * breaks up
     *
     * @param surge how far into its breakup it is (see {@link RealisticAurora.Moment#surge})
     */
    public static float distanceAt(Ribbon ribbon, float surge) {
        return ribbon.distance() * (1F - SURGE_DRAW * Math.clamp(surge, 0F, 1F));
    }

    /**
     * How high the lower edge is at a point in its night: lowered as the night breaks up
     */
    public static float lowerEdgeAt(float surge) {
        return LOWER_EDGE - EDGE_DROP * Math.clamp(surge, 0F, 1F);
    }

    /**
     * How far a fold reaches across, in kilometers, for it to bend the ribbon at most {@code slope} kilometers across for
     * each kilometer along
     */
    private static float amplitude(float slope, float wavelength) {
        return slope * wavelength / Mth.TWO_PI;
    }

    private static float sign(IRandomizer random) {
        return random.nextBoolean() ? 1F : -1F;
    }
}
