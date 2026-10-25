package org.orecruncher.dsurround.effects.lightning;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.random.IRandomizer;

/**
 * One flash of lightning, from the leader feeling its way out to the last of its glow: where it is, its shape, and how
 * bright each part is at each moment.
 * <p>
 * A real flash is a faint leader working its way along the channel, then a return stroke lighting the whole of it at
 * once, and often a few more strokes down the same main channel a twentieth of a second or so apart, the branches
 * barely lit again; each dies away in a few hundredths of a second. That is the flicker. A strike here follows the
 * same pattern, from the time since it began ({@code age}, in seconds).
 */
public final class Strike {

    public enum Kind {
        /** Cloud to ground */
        GROUND,
        /** Branches crawling sideways under the cloud base */
        SPIDER,
        /** Lightning hidden in the cloud, lighting it up: a glow without a channel */
        SHEET
    }

    /**
     * How long the main channel's light takes to fall to about a third, after each stroke, in seconds
     */
    static final float CHANNEL_DECAY = 0.08F;
    /**
     * The same for the branches, which fade sooner
     */
    static final float BRANCH_DECAY = 0.045F;
    /**
     * The same for the glow in the cloud
     */
    static final float GLOW_DECAY = 0.12F;
    /**
     * How bright the leader is, against a return stroke's 1
     */
    static final float LEADER_LIGHT = 0.2F;
    /**
     * How brightly a branch lights up in a stroke after the first, against the first
     */
    static final float RESTRIKE_BRANCHES = 0.12F;
    /**
     * How brightly each branch lights up against its parent
     */
    static final float BRANCH_FALLOFF = 0.8F;
    /**
     * How long the glow lingers after the last stroke, in seconds; the strike is done then
     */
    static final float AFTERGLOW = 0.5F;

    private final Kind kind;
    private final double x, y, z;
    @Nullable
    private final BoltShape shape;
    // How long the leader takes to cross the main channel, in seconds; 0 with no leader
    private final float leaderTime;
    private final float[] strokeTimes;
    private final float[] strokePeaks;
    private final CloudGlow glow;
    // How far away it is, in blocks
    private final float distance;
    // In game ticks, with the part of a tick
    private final double startTicks;
    // The game's lightning bolt it stands in for, or -1
    private final int boltId;

    Strike(Kind kind, double x, double y, double z, @Nullable BoltShape shape, float leaderTime, float[] strokeTimes,
           float[] strokePeaks, CloudGlow glow, float distance, double startTicks, int boltId) {
        if (strokeTimes.length == 0 || strokeTimes.length != strokePeaks.length)
            throw new IllegalArgumentException("a strike needs a peak for each of its strokes, and at least one");
        if (glow.strokes() != strokeTimes.length)
            throw new IllegalArgumentException("the cloud's glow needs a weight for each stroke");
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.shape = shape;
        this.leaderTime = leaderTime;
        this.strokeTimes = strokeTimes;
        this.strokePeaks = strokePeaks;
        this.glow = glow;
        this.distance = distance;
        this.startTicks = startTicks;
        this.boltId = boltId;
    }

    /**
     * When each stroke comes, in seconds from the start: the first as the leader arrives, each of the rest 40 to 130
     * milliseconds after the one before
     *
     * @param strokes how many: 1 for a single stroke, without a flicker
     */
    static float[] strokeTimes(float leaderTime, int strokes, IRandomizer random) {
        var times = new float[Math.max(1, strokes)];
        times[0] = leaderTime;
        for (int i = 1; i < times.length; i++)
            times[i] = times[i - 1] + random.nextFloat(0.04F, 0.13F);
        return times;
    }

    /**
     * How bright each stroke is: the first at full, the rest a little less
     */
    static float[] strokePeaks(int strokes, IRandomizer random) {
        var peaks = new float[Math.max(1, strokes)];
        peaks[0] = 1F;
        for (int i = 1; i < peaks.length; i++)
            peaks[i] = random.nextFloat(0.55F, 0.95F);
        return peaks;
    }

    /**
     * How many strokes a flash has: 1 to 2 in a weak storm, up to 5 in the strongest
     */
    static int strokeCount(float intensity, IRandomizer random) {
        int most = 1 + Math.round(Math.clamp(intensity, 0F, 1F) * 4F);
        return 1 + random.nextInt(most);
    }

    public Kind kind() {
        return this.kind;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    @Nullable
    public BoltShape shape() {
        return this.shape;
    }

    public float distance() {
        return this.distance;
    }

    public CloudGlow glow() {
        return this.glow;
    }

    public int boltId() {
        return this.boltId;
    }

    public int strokes() {
        return this.strokeTimes.length;
    }

    /**
     * Seconds since it began, at a game time in ticks
     */
    public float age(double nowTicks) {
        return (float) ((nowTicks - this.startTicks) / 20D);
    }

    /**
     * Whether it is over: past its last stroke's glow
     */
    public boolean isDone(float age) {
        return age > this.strokeTimes[this.strokeTimes.length - 1] + AFTERGLOW;
    }

    /**
     * How many strokes come after {@code from} and by {@code to}, in seconds since it began
     */
    public int strokesBetween(float from, float to) {
        int count = 0;
        for (var time : this.strokeTimes)
            if (time > from && time <= to)
                count++;
        return count;
    }

    /**
     * Whether the leader is still feeling its way out, before the first stroke
     */
    public boolean inLeader(float age) {
        return age < this.strokeTimes[0];
    }

    /**
     * How far the leader has got, in the units of a channel's arrival (see {@link BoltShape.Channel#arrival})
     */
    public float leaderReach(float age) {
        return this.leaderTime <= 0F ? Float.MAX_VALUE : age / this.leaderTime;
    }

    /**
     * How bright a point in the bolt is: 0 where nothing shows, 1 at a stroke's peak, and more where strokes overlap.
     * Before the first stroke only what the leader has reached shows, faintly.
     *
     * @param depth   the channel's depth (0 for the main channel)
     * @param arrival when the leader reaches the point (see {@link BoltShape.Channel#arrival})
     */
    public float channelBrightness(int depth, float arrival, float age) {
        if (age < 0F)
            return 0F;
        if (this.inLeader(age))
            return arrival <= this.leaderReach(age) ? LEADER_LIGHT * (depth == 0 ? 1F : BRANCH_FALLOFF) : 0F;
        float decay = depth == 0 ? CHANNEL_DECAY : BRANCH_DECAY;
        float branch = depth == 0 ? 1F : (float) Math.pow(BRANCH_FALLOFF, depth);
        float light = 0F;
        for (int i = 0; i < this.strokeTimes.length; i++) {
            float since = age - this.strokeTimes[i];
            if (since < 0F)
                break;
            float peak = this.strokePeaks[i] * (depth == 0 || i == 0 ? branch : branch * RESTRIKE_BRANCHES);
            light += peak * (float) Math.exp(-since / decay);
        }
        return light;
    }

    /**
     * How brightly a patch of the cloud's glow shows, 0 to about 1: each stroke lights it by its weight for that
     * stroke, and it fades more slowly than the channel
     */
    public float glowBrightness(int patch, float age) {
        float light = 0F;
        for (int i = 0; i < this.strokeTimes.length; i++) {
            float since = age - this.strokeTimes[i];
            if (since < 0F)
                break;
            light += this.strokePeaks[i] * this.glow.weight(patch, i) * (float) Math.exp(-since / GLOW_DECAY);
        }
        return Math.min(light, 1.5F) * this.glow.strength();
    }
}
