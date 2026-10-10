package org.orecruncher.dsurround.effects.particles;

import net.minecraft.util.RandomSource;

/**
 * When a firefly flashes. Like real fireflies, it is dark most of the time and gives short flashes, each rising
 * quickly and fading away more slowly, in a rhythm that depends on its kind:
 * <ul>
 *     <li>{@link Pattern#SINGLE}: one flash, fading over about a second, every five or six seconds, like the common
 *     eastern firefly (Photinus pyralis)</li>
 *     <li>{@link Pattern#DOUBLE}: two quick pulses close together, every three or four seconds</li>
 *     <li>{@link Pattern#QUICK}: short, slightly irregular flashes every second and a half or so</li>
 * </ul>
 * A firefly lives for a few flashes, and until its last has faded. Times are in ticks.
 */
public final class FireflyFlash {

    /**
     * A kind of flashing.
     *
     * @param pulses     pulses in each flash
     * @param pulseGap   ticks between the pulses of a flash
     * @param rise       ticks for a pulse to reach full brightness
     * @param decay      how quickly a pulse fades: ticks to fall to about a third
     * @param minPeriod  shortest time from one flash to the next
     * @param maxPeriod  longest
     * @param minFlashes fewest flashes in a firefly's life
     * @param maxFlashes most
     * @param jitter     how far each flash may come early or late, so the rhythm isn't exact
     * @param weight     how common this kind is, relative to the others
     */
    public enum Pattern {
        SINGLE(1, 0, 3, 8F, 100, 120, 2, 3, 4, 5),
        DOUBLE(2, 10, 2, 4F, 60, 80, 2, 3, 3, 3),
        QUICK(1, 0, 1, 3.5F, 25, 40, 3, 6, 6, 2);

        final int pulses;
        final int pulseGap;
        final int rise;
        final float decay;
        final int minPeriod;
        final int maxPeriod;
        final int minFlashes;
        final int maxFlashes;
        final int jitter;
        final int weight;

        Pattern(int pulses, int pulseGap, int rise, float decay, int minPeriod, int maxPeriod, int minFlashes,
                int maxFlashes, int jitter, int weight) {
            this.pulses = pulses;
            this.pulseGap = pulseGap;
            this.rise = rise;
            this.decay = decay;
            this.minPeriod = minPeriod;
            this.maxPeriod = maxPeriod;
            this.minFlashes = minFlashes;
            this.maxFlashes = maxFlashes;
            this.jitter = jitter;
            this.weight = weight;
        }

        static Pattern pick(RandomSource random) {
            int total = 0;
            for (var p : values())
                total += p.weight;
            int roll = random.nextInt(total);
            for (var p : values()) {
                roll -= p.weight;
                if (roll < 0)
                    return p;
            }
            return SINGLE;
        }
    }

    // The first flash comes soon after a firefly appears, so it is seen
    private static final int MIN_FIRST = 5;
    private static final int MAX_FIRST = 25;
    // How long after its last pulse begins a firefly lasts: long enough for the pulse to fade to almost nothing
    private static final float FADE_OUT_DECAYS = 5F;
    // The "J" stroke: a firefly dips just before it flashes, then rises while lit. It looks this far ahead to dip.
    private static final float ANTICIPATION = 6F;

    private final Pattern pattern;
    private final float[] pulseStarts;
    private final int lifetime;

    public FireflyFlash(RandomSource random) {
        this(Pattern.pick(random), random);
    }

    public FireflyFlash(Pattern pattern, RandomSource random) {
        this.pattern = pattern;

        var flashes = pattern.minFlashes + random.nextInt(pattern.maxFlashes - pattern.minFlashes + 1);
        this.pulseStarts = new float[flashes * pattern.pulses];
        float flashStart = MIN_FIRST + random.nextInt(MAX_FIRST - MIN_FIRST + 1);
        int i = 0;
        for (int f = 0; f < flashes; f++) {
            for (int p = 0; p < pattern.pulses; p++)
                this.pulseStarts[i++] = flashStart + p * pattern.pulseGap;
            var period = pattern.minPeriod + random.nextInt(pattern.maxPeriod - pattern.minPeriod + 1);
            var jitter = pattern.jitter > 0 ? random.nextInt(pattern.jitter * 2 + 1) - pattern.jitter : 0;
            flashStart += period + jitter;
        }

        var last = this.pulseStarts[this.pulseStarts.length - 1];
        this.lifetime = (int) Math.ceil(last + pattern.rise + pattern.decay * FADE_OUT_DECAYS);
    }

    public Pattern pattern() {
        return this.pattern;
    }

    /**
     * How long the firefly lives, in ticks.
     */
    public int lifetime() {
        return this.lifetime;
    }

    /**
     * When each pulse begins, in ticks from the firefly appearing.
     */
    public float[] pulseStarts() {
        return this.pulseStarts.clone();
    }

    /**
     * How brightly the firefly is lit, 0 (dark) to 1.
     *
     * @param age ticks since it appeared, including the part of a tick for smooth drawing
     */
    public float brightness(float age) {
        float brightness = 0F;
        for (var start : this.pulseStarts) {
            var dt = age - start;
            if (dt < 0F)
                break;
            brightness = Math.max(brightness, this.pulse(dt));
        }
        return brightness;
    }

    /**
     * How much it climbs (positive) or sinks: a dip just before each flash, then a rise while it is lit.
     */
    public float climb(float age) {
        return 1.2F * this.brightness(age) - 0.4F * this.brightness(age + ANTICIPATION);
    }

    private float pulse(float dt) {
        var rise = this.pattern.rise;
        if (dt < rise) {
            var t = dt / rise;
            return t * t * (3F - 2F * t);
        }
        return (float) Math.exp(-(dt - rise) / this.pattern.decay);
    }
}
