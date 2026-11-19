// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.rainbow;

/**
 * The color a rainbow sends back toward the viewer at each angle from the point opposite the sun, worked out from how
 * sunlight goes through raindrops rather than painted as bands. For each wavelength of light, water bends it by its own
 * amount (red least, violet most); a ray entering a drop is bent going in, reflected once inside it (the primary bow)
 * or twice (the secondary), and bent again going out. Rays striking the drop at every height between its middle and
 * its edge come out at angles that pile up at one angle, the least they can turn back by: about 42 degrees for the
 * primary, 51 for the secondary, a little different for each color. So:
 * <ul>
 *     <li>the primary is red outside and violet inside; the secondary is reversed, and fainter, having lost more
 *     light in its second reflection</li>
 *     <li>inside the primary the sky is brighter (rays that turned back by more than the least), and so is outside
 *     the secondary; between the two no ray of either comes out, and the sky there is darker: Alexander's band</li>
 * </ul>
 * How much of the light each surface passes and reflects is Fresnel's, for each polarization. The sun is half a
 * degree across, and real drops are of many sizes, so the bands are softened by {@link #BLUR_DEGREES}. The light is
 * turned into a color with the eye's color matching functions, sunlight taken as white, and colors more saturated than
 * a screen can show lose what it can't (paling them toward white instead left the bow looking white).
 * <p>
 * Two rows of {@link #SIZE} colors over 0 to {@link #MAX_DEGREES}: the primary bow's light, then the secondary's, in
 * linear light, scaled so that the brightest channel of the primary is 1.
 */
public final class RainbowSpectrum {

    /**
     * Colors in each row
     */
    public static final int SIZE = 256;
    /**
     * The angle from the point opposite the sun at the end of each row, in degrees; a row starts at 0
     */
    public static final float MAX_DEGREES = 60F;
    /**
     * How far the bands are softened, in degrees: the sun's disk, half a degree across, and drops of different sizes.
     * More (0.55) ran the colors together
     */
    static final double BLUR_DEGREES = 0.3;

    // Wavelengths, in nanometers
    private static final int FIRST_WAVELENGTH = 400;
    private static final int LAST_WAVELENGTH = 700;
    private static final int WAVELENGTH_STEP = 5;
    // Heights at which rays strike a drop, from its middle to its edge
    private static final int RAYS = 20000;
    // Finer bins than the rows', before blurring
    private static final int BINS = SIZE * 8;

    private final float[] primary;
    private final float[] secondary;

    private RainbowSpectrum(float[] primary, float[] secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    /**
     * The primary bow's light, {@link #SIZE} colors of three floats each
     */
    public float[] primary() {
        return this.primary.clone();
    }

    /**
     * The secondary bow's light, as {@link #primary}
     */
    public float[] secondary() {
        return this.secondary.clone();
    }

    /**
     * The angle, in degrees, of an index into a row
     */
    public static float degreesOf(int index) {
        return (index + 0.5F) * MAX_DEGREES / SIZE;
    }

    /**
     * The index into a row nearest an angle, in degrees
     */
    public static int indexOf(float degrees) {
        return Math.clamp((int) (degrees / MAX_DEGREES * SIZE), 0, SIZE - 1);
    }

    /**
     * Water's refractive index for light of a wavelength in nanometers (a fit to measurements at 20 C): about 1.343
     * for violet, 1.331 for red
     */
    static double refractiveIndex(double nanometers) {
        return 1.31279 + 15.762 / nanometers - 4382.0 / (nanometers * nanometers) + 1.1455e6 / (nanometers * nanometers * nanometers);
    }

    /**
     * Works the colors out. Takes a few milliseconds: done once.
     */
    public static RainbowSpectrum create() {
        int wavelengths = (LAST_WAVELENGTH - FIRST_WAVELENGTH) / WAVELENGTH_STEP + 1;
        // Light at each angle, each wavelength
        var light1 = new double[wavelengths][BINS];
        var light2 = new double[wavelengths][BINS];
        double binRadians = Math.toRadians(MAX_DEGREES) / BINS;

        for (int w = 0; w < wavelengths; w++) {
            double n = refractiveIndex(FIRST_WAVELENGTH + w * WAVELENGTH_STEP);
            for (int k = 0; k < RAYS; k++) {
                // How far from the drop's middle the ray strikes, 0 to 1; the light striking a ring of the drop is
                // as its circumference
                double b = (k + 0.5) / RAYS;
                double ring = b;
                double i = Math.asin(b);
                double r = Math.asin(b / n);
                // How much is reflected at each surface, for each polarization; the inside reflections are the same
                // as the outside's (Stokes)
                double cosI = Math.cos(i), cosR = Math.cos(r);
                double rs = square((cosI - n * cosR) / (cosI + n * cosR));
                double rp = square((cosR - n * cosI) / (cosR + n * cosI));
                double once = (square(1 - rs) * rs + square(1 - rp) * rp) / 2;
                double twice = (square(1 - rs) * rs * rs + square(1 - rp) * rp * rp) / 2;
                // The angle from the point opposite the sun it comes out at
                double theta1 = 4 * r - 2 * i;
                double theta2 = Math.PI + 2 * i - 6 * r;
                add(light1[w], theta1, binRadians, ring * once);
                add(light2[w], theta2, binRadians, ring * twice);
            }
            perSolidAngle(light1[w], binRadians);
            perSolidAngle(light2[w], binRadians);
        }

        var primary = toColors(light1, binRadians);
        var secondary = toColors(light2, binRadians);
        // The primary's brightest channel is 1
        float max = 0F;
        for (var value : primary)
            max = Math.max(max, value);
        for (int j = 0; j < primary.length; j++) {
            primary[j] /= max;
            secondary[j] /= max;
        }
        return new RainbowSpectrum(primary, secondary);
    }

    private static void add(double[] bins, double theta, double binRadians, double weight) {
        int bin = (int) (theta / binRadians);
        if (bin >= 0 && bin < bins.length)
            bins[bin] += weight;
    }

    // From light falling in each bin to light for each bit of the sky: a bin is a ring around the point opposite the sun
    private static void perSolidAngle(double[] bins, double binRadians) {
        for (int j = 0; j < bins.length; j++)
            bins[j] /= Math.sin((j + 0.5) * binRadians);
    }

    // Blurs each wavelength's light, turns it into colors and takes it down to SIZE colors
    private static float[] toColors(double[][] light, double binRadians) {
        int wavelengths = light.length;
        var blurred = new double[wavelengths][];
        for (int w = 0; w < wavelengths; w++)
            blurred[w] = blur(light[w], Math.toRadians(BLUR_DEGREES) / binRadians);

        // White: every wavelength at once, at the same strength
        var white = new double[3];
        for (int w = 0; w < wavelengths; w++)
            addColor(white, FIRST_WAVELENGTH + w * WAVELENGTH_STEP, 1);

        var colors = new float[SIZE * 3];
        int per = BINS / SIZE;
        var rgb = new double[3];
        for (int index = 0; index < SIZE; index++) {
            rgb[0] = rgb[1] = rgb[2] = 0;
            for (int bin = index * per; bin < (index + 1) * per; bin++)
                for (int w = 0; w < wavelengths; w++)
                    addColor(rgb, FIRST_WAVELENGTH + w * WAVELENGTH_STEP, blurred[w][bin] / per);
            // White balanced: sunlight is white
            for (int c = 0; c < 3; c++)
                rgb[c] /= white[c];
            paleIntoRange(rgb);
            for (int c = 0; c < 3; c++)
                colors[index * 3 + c] = (float) rgb[c];
        }
        return colors;
    }

    private static double[] blur(double[] bins, double sigmaBins) {
        int reach = (int) Math.ceil(sigmaBins * 3);
        var kernel = new double[reach * 2 + 1];
        double sum = 0;
        for (int j = -reach; j <= reach; j++) {
            kernel[j + reach] = Math.exp(-0.5 * square(j / sigmaBins));
            sum += kernel[j + reach];
        }
        var out = new double[bins.length];
        for (int j = 0; j < bins.length; j++) {
            double value = 0;
            for (int k = -reach; k <= reach; k++) {
                int at = j + k;
                if (at >= 0 && at < bins.length)
                    value += bins[at] * kernel[k + reach];
            }
            out[j] = value / sum;
        }
        return out;
    }

    // Adds light of a wavelength, as linear sRGB, by way of the eye's color matching functions (Wyman, Sloan and
    // Shirley's fit to CIE 1931)
    private static void addColor(double[] rgb, double nm, double amount) {
        double x = 1.056 * lobe(nm, 599.8, 37.9, 31.0) + 0.362 * lobe(nm, 442.0, 16.0, 26.7) - 0.065 * lobe(nm, 501.1, 20.4, 26.2);
        double y = 0.821 * lobe(nm, 568.8, 46.9, 40.5) + 0.286 * lobe(nm, 530.9, 16.3, 31.1);
        double z = 1.217 * lobe(nm, 437.0, 11.8, 36.0) + 0.681 * lobe(nm, 459.0, 26.0, 13.8);
        rgb[0] += amount * (3.2406 * x - 1.5372 * y - 0.4986 * z);
        rgb[1] += amount * (-0.9689 * x + 1.8758 * y + 0.0415 * z);
        rgb[2] += amount * (0.0557 * x - 0.2040 * y + 1.0570 * z);
    }

    private static double lobe(double nm, double mean, double below, double above) {
        double t = (nm - mean) / (nm < mean ? below : above);
        return Math.exp(-0.5 * t * t);
    }

    // A color a screen can't show (a channel below 0) loses what it can't show. Paling it toward gray instead, which
    // keeps its brightness, left a bow that looked white
    private static void paleIntoRange(double[] rgb) {
        for (int c = 0; c < 3; c++)
            rgb[c] = Math.max(rgb[c], 0);
    }

    private static double square(double x) {
        return x * x;
    }
}
