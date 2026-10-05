package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.orecruncher.dsurround.lib.random.Randomizer;


/**
 * One night's aurora: its colours and the shape of its curtains. Everything is drawn from the night's number, so
 * everyone in a world sees the same aurora on the same night.
 * <p>
 * Curtains are laid out around the viewer, who is at the origin, in "sky units": a curtain's lower edge is about one
 * unit up and it stands one to a bit over one unit tall. Each one follows a path across the sky, square to a
 * heading, with a slow fold along it and a ripple that travels along it. The renderer scales this up to just inside
 * where the sky is drawn ({@link #SCALE}).
 *
 * @param night          the night's number (the day, counted from the world's start)
 * @param palette        its colours
 * @param heading        which way it lies from the viewer, in radians: 0 is north (toward -Z), and it is never more
 *                       than {@link #MAX_HEADING} either side
 * @param distance       how far its path is from the viewer
 * @param length         how long its path is
 * @param altitude       how high its lower edge is
 * @param height         how tall the first curtain is
 * @param bands          how many curtains, side by side (1 to 3)
 * @param foldAmount     how far the path folds in and out
 * @param foldFrequency  how many folds along it
 * @param phase          where along their cycle the folds and ripples start
 * @param brightness     how bright it is, at most
 * @param textureOffset  where in the shader's pattern it starts, so each night's rays differ
 */
public record Aurora(long night, AuroraPalette palette, float heading, float distance, float length, float altitude,
                     float height, int bands, float foldAmount, float foldFrequency, float phase, float brightness,
                     float textureOffset) {

    /**
     * From sky units to blocks. The farthest a curtain can reach is a little under {@link #MAX_REACH} units, which
     * puts it inside the stars (100 blocks) and the nearest far plane (128 blocks, at the least render distance).
     */
    public static final float SCALE = 11F;
    public static final float MAX_REACH = 95F / SCALE;

    // Curtain layout. Auroras lie toward the pole, so a curtain is always somewhere to the north.
    public static final float MAX_HEADING = 30F * Mth.DEG_TO_RAD;
    private static final float BAND_SPACING = 0.6F;
    private static final float BAND_RISE = 0.1F;
    private static final float BAND_SHRINK = 0.15F;
    private static final float LEAN = 0.25F;

    // The ripple that travels along a curtain
    private static final float RIPPLE_AMOUNT = 0.1F;
    private static final float RIPPLE_WAVELENGTH = 2.5F;
    private static final float RIPPLE_SPEED = 0.5F;
    // How quickly the folds drift
    private static final float FOLD_DRIFT = 0.04F;

    // Hashing constants (from SplitMix64), so seeds spread well from consecutive night numbers
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long CHANCE_SALT = 0x5DEECE66DL;

    /**
     * Whether there is an aurora on the given night.
     *
     * @param chance percent of nights with an aurora, 0 to 100
     */
    public static boolean appears(long night, int chance) {
        return Math.floorMod(mix(night ^ CHANCE_SALT), 100L) < chance;
    }

    /**
     * The aurora for the given night.
     *
     * @param maxBands the most curtains it may have, 1 to 3
     */
    public static Aurora create(long night, int maxBands) {
        var random = Randomizer.create(mix(night));
        var palette = AuroraPalette.PALETTES.get(random.nextInt(AuroraPalette.PALETTES.size()));
        var heading = random.nextFloat(-MAX_HEADING, MAX_HEADING);
        var distance = random.nextFloat(1.5F, 4F);
        var length = random.nextFloat(6F, 10F);
        var altitude = random.nextFloat(0.8F, 1.2F);
        var height = random.nextFloat(0.8F, 1.4F);
        var bands = 1 + random.nextInt(Math.clamp(maxBands, 1, 3));
        var foldAmount = random.nextFloat(0.15F, 0.45F);
        var foldFrequency = random.nextFloat(0.75F, 1.75F);
        var phase = random.nextFloat() * Mth.TWO_PI;
        var brightness = random.nextFloat(0.7F, 1F);
        var textureOffset = random.nextFloat() * 1000F;
        return new Aurora(night, palette, heading, distance, length, altitude, height, bands, foldAmount, foldFrequency,
                phase, brightness, textureOffset);
    }

    /**
     * Where a curtain's lower edge is, in sky units.
     *
     * @param band which curtain, from 0 (nearest)
     * @param s    how far along it, -1 (one end) to 1 (the other)
     * @param time seconds, for the drifting folds and the travelling ripple
     * @param out  set to the point
     */
    public Vector3f bottom(int band, float s, float time, Vector3f out) {
        var along = s * this.length * 0.5F;
        var bandPhase = this.phase + band * 1.7F;
        var fold = this.foldAmount * Mth.sin(s * Mth.PI * this.foldFrequency + bandPhase + time * FOLD_DRIFT);
        var ripple = RIPPLE_AMOUNT * Mth.sin(along * Mth.TWO_PI / RIPPLE_WAVELENGTH - time * RIPPLE_SPEED + bandPhase);
        var offset = this.distance + band * BAND_SPACING + fold + ripple;
        return place(along, offset, this.altitude + band * BAND_RISE, out);
    }

    /**
     * Where a curtain's top is, given where its lower edge is: straight up, leaning a little away from the viewer.
     */
    public Vector3f top(int band, Vector3f bottom, Vector3f out) {
        var h = this.bandHeight(band);
        var lean = h * LEAN;
        return out.set(bottom.x + this.outwardX() * lean, bottom.y + h, bottom.z + this.outwardZ() * lean);
    }

    public float bandHeight(int band) {
        return this.height * (1F - band * BAND_SHRINK);
    }

    /**
     * How bright each curtain is relative to the first.
     */
    public float bandBrightness(int band) {
        return 1F - band * 0.2F;
    }

    /**
     * How much a curtain fades toward its ends: 1 for most of it, falling to 0 at the very end.
     */
    public static float taper(float s) {
        var t = Math.clamp((Math.abs(s) - 0.5F) / 0.5F, 0F, 1F);
        return 1F - t * t * (3F - 2F * t);
    }

    /**
     * Distance along a curtain, for the shader's pattern: in sky units, and different for each curtain and night.
     */
    public float textureU(int band, float s) {
        return this.textureOffset + band * 37F + s * this.length * 0.5F;
    }

    private Vector3f place(float along, float outward, float y, Vector3f out) {
        // Outward is the heading; along is square to it
        var ox = this.outwardX();
        var oz = this.outwardZ();
        return out.set(ox * outward - oz * along, y, oz * outward + ox * along);
    }

    private float outwardX() {
        return Mth.sin(this.heading);
    }

    private float outwardZ() {
        return -Mth.cos(this.heading);
    }


    private static long mix(long value) {
        var z = value * GOLDEN + GOLDEN;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
