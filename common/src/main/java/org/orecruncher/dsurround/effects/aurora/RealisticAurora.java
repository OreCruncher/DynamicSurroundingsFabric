package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.Mixers;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.ArrayList;
import java.util.List;

/**
 * One night's realistic aurora: arcs of light high in the sky, at the heights and distances the real ones are, and how
 * they change through the night. Drawn from the night's number, so everyone in a world sees the same aurora on the
 * same night.
 * <p>
 * Everything is in kilometres, around a viewer on the ground of a round Earth ({@link #EARTH_RADIUS}). An arc is a thin
 * sheet that runs east-west, square to north, a {@link Arc#distance} north of the viewer; its lower edge is about
 * {@link #LOWER_EDGE} up, and it rises along the Earth's magnetic field, leaning a little south as it goes
 * ({@link #FIELD_TILT}). The arcs stay with the viewer: walking a few kilometres changes nothing that could be seen
 * of something hundreds away.
 * <p>
 * Through the night ({@link #at}): the arcs drift slowly closer; then, unless the night is quiet, the aurora breaks up
 * at {@link #onset}: it brightens suddenly, comes closer, moves faster and shows more color, for a while; then it fades,
 * its rays going diffuse and its patches pulsing. How much it does is its {@link #activity}.
 * <p>
 * North and east here are magnetic: the arcs run along magnetic east-west and lean toward magnetic south. Magnetic north
 * is {@link #declination} from true north, so a night's arcs lie at a slant, one end higher in the sky than the other.
 *
 * @param night       the night's number (the day, counted from the world's start)
 * @param activity    how active it is
 * @param color      its color character
 * @param colorRoll  the night's roll its color character comes from, 0 to 1, with how active it is
 * @param declination how far magnetic north is from true north, in radians, east positive
 * @param onset       when in the night it breaks up, 0 (dusk) to 1 (dawn)
 * @param peakLength  how long it stays at its height after breaking up, as a share of the night
 * @param arcs        its arcs, nearest first
 */
public record RealisticAurora(long night, AuroraActivity activity, AuroraColor color, float colorRoll,
                              float declination, float onset, float peakLength, List<Arc> arcs) {

    /**
     * The Earth's radius, in kilometres
     */
    public static final float EARTH_RADIUS = 6371F;
    /**
     * How high an arc's lower edge is, in kilometres: where the green light begins
     */
    public static final float LOWER_EDGE = 105F;
    /**
     * How far an arc leans south for each kilometre it rises: the magnetic field's slope at high latitudes, about 13
     * degrees from straight up
     */
    public static final float FIELD_TILT = 0.23F;
    /**
     * The most arcs there can be: the shader has room for this many
     */
    public static final int MAX_ARCS = 4;

    // Keeps the realistic aurora's shapes apart from the classic one's, though both come from the night's number; and
    // how active it is apart from its shapes, so changing the storm chance doesn't change them
    private static final long SHAPE_SALT = 0x7265616CL;
    private static final long ACTIVITY_SALT = 0x61637476L;
    // And its color character, its declination and its arcs' bows, each apart from the rest, so a night that had an
    // aurora before they were added keeps its arcs
    private static final long COLOR_SALT = 0x636F6C72L;
    private static final long DECLINATION_SALT = 0x6465636CL;
    private static final long BOW_SALT = 0x626F7773L;

    /**
     * The most magnetic north is from true north, either way, in radians
     */
    public static final float MAX_DECLINATION = 20F * Mth.DEG_TO_RAD;
    /**
     * How far along an arc, either way, its bow has bent it half as far as it goes, in kilometres: it bends little near
     * the viewer and most toward its ends (in the shader, BOW_LENGTH)
     */
    public static final float BOW_LENGTH = 600F;
    /**
     * The most an arc's ends bend north or south, in kilometres
     */
    static final float MAX_BOW = 200F;
    /**
     * How strong the blue-violet glow of the sunlit tops of the rays is, next to the green
     */
    static final float SUNLIT = 0.5F;
    /**
     * The highest the Earth's shadow is taken to be, in kilometres: far above everything, the sun well down
     */
    static final float MAX_SHADOW = 5000F;
    /**
     * How much a full moon high in the sky washes out a faint aurora (see {@link #moonWashout})
     */
    static final float MOON_WASHOUT = 0.06F;

    /**
     * How quickly an arc's large folds drift along their cycle, in radians a second: one cycle in about ten minutes, a
     * night (in the shader, FOLD_DRIFT)
     */
    public static final float FOLD_DRIFT = 0.01F;
    /**
     * How far along an arc from one curl to the next, in kilometres (in the shader, CURL_WAVELENGTH)
     */
    public static final float CURL_WAVELENGTH = 25F;
    /**
     * The farthest north an arc starts, in kilometres: beyond this it would be barely over the horizon
     */
    static final float FARTHEST = 750F;
    /**
     * The steepest an arc's folds together bend it, in kilometres north for each kilometre east. A line of sight
     * slanting across a steeply bent arc meets it at points that jump along it from one pixel to the next, and its rays
     * zig-zag; real arcs seen from hundreds of kilometres off are gentler than that
     */
    static final float MAX_SLOPE = 0.45F;

    // The night's timeline, as shares of the night. The arcs drift this many kilometres closer before the breakup (all
    // night, if there is none); the breakup takes this long to reach its height (about fifteen seconds); afterwards the
    // surge dies away over about this long
    static final float GROWTH_DRIFT = 40F;
    static final float BREAKUP_LENGTH = 0.03F;
    static final float RECOVERY_DECAY = 0.08F;
    // How strong the red high up is next to the green, and the pink fringe, before any breakup
    static final float BASE_RED = 0.35F;
    static final float BASE_FRINGE = 0.05F;
    // How far beyond the nearest arc the middle of the ray field is, as a share of how far it reaches either way
    static final float FIELD_BEYOND = 0.6F;

    /**
     * Where an aurora is in its night
     */
    public enum Phase {
        /**
         * Before the breakup: the arcs drift slowly closer. A quiet night stays in this all night
         */
        GROWTH,
        /**
         * Breaking up: brightening suddenly, coming closer, moving faster
         */
        BREAKUP,
        /**
         * At its height
         */
        EXPANSION,
        /**
         * Fading afterwards: its rays going diffuse, its patches pulsing
         */
        RECOVERY
    }

    /**
     * One arc. Its lower edge follows a path east-west, folded three ways: large folds hundreds of kilometres long that
     * drift slowly; a wave along it that travels; and small curls that travel with its rays.
     *
     * @param distance        how far north of the viewer it is, along the ground, in kilometres
     * @param foldAmplitude   how far its large folds reach north and south of that, in kilometres
     * @param foldWavelength  how far along it from one large fold to the next, in kilometres
     * @param phase           where along their cycle the folds start, in radians
     * @param waveAmplitude   how far the travelling wave reaches north and south, in kilometres
     * @param waveWavelength  how far along it from one crest of the wave to the next, in kilometres
     * @param waveSpeed       how fast the wave travels east (west if negative), in kilometres a second, when calm
     * @param curlAmplitude   how far its curls reach north and south, in kilometres
     * @param brightness      how bright it is, relative to a typical arc
     * @param thickness       how thick the sheet is, north to south, in kilometres
     * @param rayOffset       where in the shader's pattern its rays start, in kilometres, so each night's rays differ
     * @param raySpeed        how fast its rays and curls move east (west if negative), in kilometres a second, when calm
     * @param bow             how far its ends bend north (south if negative), in kilometres: as pieces of the ring the
     *                        aurora makes around the magnetic pole, seen from within it or without
     */
    public record Arc(float distance, float foldAmplitude, float foldWavelength, float phase,
                      float waveAmplitude, float waveWavelength, float waveSpeed, float curlAmplitude,
                      float brightness, float thickness, float rayOffset, float raySpeed, float bow) {

        /**
         * How far north of the viewer the arc's lower edge is, in kilometres (in the shader, arc_north)
         *
         * @param along     kilometres east of the viewer
         * @param time      seconds, for the large folds' drift
         * @param approach  how much closer the arcs have come, in kilometres (see {@link Moment#approach})
         * @param wave      how far the wave has travelled east, in kilometres (see {@link RealisticAuroraMotion})
         * @param curl      how far the curls have travelled east, in kilometres
         */
        public float northAt(float along, float time, float approach, float wave, float curl) {
            float north = this.distance - approach;
            north -= this.foldAmplitude * (float) Math.sin(along * Mth.TWO_PI / this.foldWavelength + this.phase + time * FOLD_DRIFT);
            north -= this.waveAmplitude * (float) Math.sin((along - wave) * Mth.TWO_PI / this.waveWavelength);
            north -= this.curlAmplitude * (float) Math.sin((along - curl) * Mth.TWO_PI / CURL_WAVELENGTH + this.phase * 3F);
            return north + this.bowAt(along);
        }

        /**
         * How far its bow bends it north (south if negative) {@code along} kilometres east of the viewer: hardly at all
         * near the viewer, toward {@link #bow} at its ends (in the shader, arc_bow)
         */
        public float bowAt(float along) {
            float a2 = along * along;
            return this.bow * a2 / (a2 + BOW_LENGTH * BOW_LENGTH);
        }

        /**
         * The most its lower edge strays north or south of where it is, in kilometres
         */
        public float reach() {
            return this.foldAmplitude + this.waveAmplitude + this.curlAmplitude;
        }

        /**
         * The steepest its folds together bend it, in kilometres north for each kilometre east
         */
        public float steepest() {
            return Mth.TWO_PI * (this.foldAmplitude / this.foldWavelength + this.waveAmplitude / this.waveWavelength
                    + this.curlAmplitude / CURL_WAVELENGTH);
        }
    }

    /**
     * How the aurora is at a point in its night: what the shader is given, besides the arcs themselves.
     *
     * @param phase       where it is in its night
     * @param surge       how far into its breakup it is: 0 before, rising to 1 at its height, dying away after
     * @param approach    how much closer than they started the arcs have come, in kilometres
     * @param gain        how much brighter (or dimmer) the arcs are than their own brightness
     * @param speed       how much faster the waves, curls and rays move than when calm
     * @param rayContrast how distinct the rays are, 0 (a smooth glow) to 1
     * @param pulse       how strongly patches pulse, 0 to 1
     * @param red         how strong the red high up is next to the green
     * @param fringe      how strong the pink fringe at the lower edge is
     * @param lowerEdge   how high the lower edge is, in kilometres
     * @param green       how strong the green is, next to a usual night's (see {@link AuroraColor})
     * @param rayField    how strong the ray field is, 0 (none) to 1
     * @param fieldNorth  how far north of the viewer the middle of the ray field is, in kilometres (negative: south)
     * @param fieldWidth  how far the ray field reaches north and south of its middle, in kilometres
     */
    public record Moment(Phase phase, float surge, float approach, float gain, float speed, float rayContrast,
                         float pulse, float red, float fringe, float lowerEdge, float green,
                         float rayField, float fieldNorth, float fieldWidth) {
    }

    /**
     * The aurora for the given night: one to {@link #MAX_ARCS} arcs side by side, the nearest brightest, each farther
     * one lower in the sky and fainter
     *
     * @param stormChance percent of nights that are storms
     */
    public static RealisticAurora create(long night, int stormChance) {
        var activity = activityOf(night, stormChance);
        float colorRoll = Randomizer.create(Mixers.splitMix64(night ^ COLOR_SALT)).nextFloat();
        float declination = Randomizer.create(Mixers.splitMix64(night ^ DECLINATION_SALT)).nextFloat(-MAX_DECLINATION, MAX_DECLINATION);
        var bows = Randomizer.create(Mixers.splitMix64(night ^ BOW_SALT));
        var random = Randomizer.create(Mixers.splitMix64(night ^ SHAPE_SALT));
        // Mostly one or two; four rarely. A storm has at least two
        int roll = random.nextInt(100);
        int count = roll < 40 ? 1 : roll < 75 ? 2 : roll < 95 ? 3 : 4;
        if (activity == AuroraActivity.STORM)
            count = Math.max(count, 2);
        float onset = random.nextFloat(0.3F, 0.6F);
        float peakLength = random.nextFloat(0.1F, 0.2F);

        var arcs = new ArrayList<Arc>(count);
        float distance = random.nextFloat(220F, 450F);
        float brightness = random.nextFloat(0.8F, 1.1F);
        for (int i = 0; i < count && distance <= FARTHEST; i++) {
            // Each fold's size from how steeply it bends the arc (see MAX_SLOPE)
            float foldWavelength = random.nextFloat(300F, 800F);
            float waveWavelength = random.nextFloat(100F, 220F);
            arcs.add(new Arc(
                    distance,
                    amplitude(random.nextFloat(0.05F, 0.2F), foldWavelength),
                    foldWavelength,
                    random.nextFloat() * Mth.TWO_PI,
                    amplitude(random.nextFloat(0.04F, 0.15F), waveWavelength),
                    waveWavelength,
                    random.nextFloat(1F, 4F) * (random.nextBoolean() ? 1F : -1F),
                    amplitude(random.nextFloat(0.02F, 0.1F), CURL_WAVELENGTH),
                    brightness,
                    random.nextFloat(4F, 7F),
                    random.nextFloat() * 1000F,
                    random.nextFloat(0.3F, 2F) * (random.nextBoolean() ? 1F : -1F),
                    bows.nextFloat(-MAX_BOW, MAX_BOW)));
            distance += random.nextFloat(40F, 140F);
            brightness *= random.nextFloat(0.55F, 0.85F);
        }
        return new RealisticAurora(night, activity, AuroraColor.of(colorRoll, activity), colorRoll, declination,
                onset, peakLength, List.copyOf(arcs));
    }

    /**
     * How active the given night's aurora is: a storm {@code stormChance} percent of the time, and otherwise quiet or
     * moderate as often as each other, and active half as often
     */
    public static AuroraActivity activityOf(long night, int stormChance) {
        var random = Randomizer.create(Mixers.splitMix64(night ^ ACTIVITY_SALT));
        if (random.nextInt(100) < stormChance)
            return AuroraActivity.STORM;
        int roll = random.nextInt(100);
        return roll < 40 ? AuroraActivity.QUIET : roll < 80 ? AuroraActivity.MODERATE : AuroraActivity.ACTIVE;
    }

    /**
     * The same aurora, as active as given: the same arcs and timeline, and the color character its roll gives for that
     */
    public RealisticAurora withActivity(AuroraActivity activity) {
        return new RealisticAurora(this.night, activity, AuroraColor.of(this.colorRoll, activity), this.colorRoll,
                this.declination, this.onset, this.peakLength, this.arcs);
    }

    /**
     * The same aurora, in the color character given
     */
    public RealisticAurora withColor(AuroraColor color) {
        return new RealisticAurora(this.night, this.activity, color, this.colorRoll, this.declination, this.onset,
                this.peakLength, this.arcs);
    }

    /**
     * When its height ends, as a share of the night
     */
    public float peakEnd() {
        return this.onset + BREAKUP_LENGTH + this.peakLength;
    }

    /**
     * A point in the night that is in the given phase: for holding the aurora there, to see it
     */
    public float progressOf(Phase phase) {
        return switch (phase) {
            case GROWTH -> this.onset * 0.5F;
            case BREAKUP -> this.onset + BREAKUP_LENGTH * 0.5F;
            case EXPANSION -> this.onset + BREAKUP_LENGTH + this.peakLength * 0.5F;
            case RECOVERY -> Math.min(this.peakEnd() + RECOVERY_DECAY, 1F);
        };
    }

    /**
     * How the aurora is at a point in the night
     *
     * @param progress how far through the night, 0 (dusk) to 1 (dawn)
     */
    public Moment at(float progress) {
        float p = Math.clamp(progress, 0F, 1F);
        var activity = this.activity;
        boolean breaks = activity.breaksUp();

        // Drifting closer until the breakup (all night, if there is none)
        float grown = breaks ? Math.min(p / this.onset, 1F) : p;

        Phase phase;
        float surge;
        // How far it has faded since its height, 0 to 1
        float recovered = 0F;
        if (!breaks || p < this.onset) {
            phase = Phase.GROWTH;
            surge = 0F;
        } else if (p < this.onset + BREAKUP_LENGTH) {
            phase = Phase.BREAKUP;
            float t = (p - this.onset) / BREAKUP_LENGTH;
            surge = t * t * (3F - 2F * t);
        } else if (p < this.peakEnd()) {
            phase = Phase.EXPANSION;
            surge = 1F;
        } else {
            phase = Phase.RECOVERY;
            surge = (float) Math.exp(-(p - this.peakEnd()) / RECOVERY_DECAY);
            recovered = 1F - surge;
        }

        // Coming closer as it breaks up, to where the activity brings the nearest arc; afterwards, drawing back half way
        float approach = GROWTH_DRIFT * grown;
        if (breaks && !this.arcs.isEmpty()) {
            float further = Math.max(0F, this.arcs.getFirst().distance() - GROWTH_DRIFT - activity.nearestAtPeak());
            approach += further * (phase == Phase.RECOVERY ? 0.5F + 0.5F * surge : surge);
        }

        float gain = (0.7F + 0.3F * grown) * (1F + activity.surgeGain() * surge) * (1F - 0.4F * recovered);
        float speed = 1F + activity.speedBoost() * surge;
        float rayContrast = Mth.lerp(recovered, 0.6F + 0.4F * surge, 0.35F);
        float pulse = activity.pulse() * recovered;
        float red = (BASE_RED + activity.extraRed() * surge) * this.color.red();
        float fringe = (BASE_FRINGE + activity.fringe() * surge) * this.color.fringe();
        float lowerEdge = LOWER_EDGE - activity.edgeDrop() * surge;

        // The ray field, as strong as the surge, mostly beyond the nearest arc: overhead in a storm
        float rayField = activity.rayField() * surge;
        float fieldWidth = activity.rayFieldWidth();
        float fieldNorth = this.arcs.isEmpty() ? 0F : this.arcs.getFirst().distance() - approach + fieldWidth * FIELD_BEYOND;
        return new Moment(phase, surge, approach, gain, speed, rayContrast, pulse, red, fringe, lowerEdge,
                this.color.green(), rayField, fieldNorth, fieldWidth);
    }

    /**
     * How far a fold reaches north and south, in kilometres, for it to bend the arc at most {@code slope} kilometres
     * north for each kilometre east
     */
    private static float amplitude(float slope, float wavelength) {
        return slope * wavelength / Mth.TWO_PI;
    }

    /**
     * How high the Earth's shadow is over the viewer, in kilometres, for the sun's celestial angle in degrees (noon 0,
     * midnight 180): above it, the tops of the rays are still in sunlight. 0 while the sun is up, about 100 kilometres
     * with it 10 degrees down, 330 at 18, and {@link #MAX_SHADOW} at most
     */
    public static float shadowHeight(float celestialAngle) {
        float angle = (float) (((celestialAngle % 360D) + 360D) % 360D);
        float below = Math.min(angle - 90F, 270F - angle);
        if (below <= 0F)
            return 0F;
        double cos = Math.cos(Math.toRadians(below));
        if (cos <= EARTH_RADIUS / (EARTH_RADIUS + (double) MAX_SHADOW))
            return MAX_SHADOW;
        return (float) (EARTH_RADIUS * (1D / cos - 1D));
    }

    /**
     * How much a bright moon washes out a faint aurora, 0 (not at all) to {@link #MOON_WASHOUT} (a full moon high up):
     * how bright, on the shader's scale, an aurora must be to show at half its strength, the sky behind it being
     * lit. As much as the moon is full, and as it is high: it is opposite the sun, so highest at midnight, and hardly
     * washes anything out low over the horizon
     *
     * @param celestialAngle the sun's celestial angle in degrees (noon 0, midnight 180)
     * @param moonSize       how full the moon is, 0 (new) to 1 (full)
     */
    public static float moonWashout(float celestialAngle, float moonSize) {
        float height = (float) -Math.cos(Math.toRadians(celestialAngle));
        return MOON_WASHOUT * Math.clamp(moonSize, 0F, 1F) * Math.clamp(height * 2F, 0F, 1F);
    }

    /**
     * How high above the horizon something is seen from the ground, in degrees, given how far away it is along the
     * ground and how high it is, both in kilometres: lower than it would be over flat ground, as the Earth curves away
     */
    public static float elevation(float groundDistance, float altitude) {
        double angle = groundDistance / (double) EARTH_RADIUS;
        double r = EARTH_RADIUS + altitude;
        // Up from the viewer and out along the ground, with the Earth's centre straight below
        double up = r * Math.cos(angle) - EARTH_RADIUS;
        double out = r * Math.sin(angle);
        return (float) Math.toDegrees(Math.atan2(up, out));
    }
}
