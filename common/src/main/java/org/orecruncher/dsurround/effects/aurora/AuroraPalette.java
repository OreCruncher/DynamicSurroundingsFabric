package org.orecruncher.dsurround.effects.aurora;

import java.util.ArrayList;
import java.util.List;

/**
 * The colours of an aurora, from its lower edge (the brightest part) up to where it fades out at the top. These are
 * the colour sets of the original Dynamic Surroundings auroras, including its warmer and cooler versions of some.
 *
 * @param bottom colour at the lower edge
 * @param middle colour halfway up
 * @param top    colour at the top
 */
public record AuroraPalette(Rgb bottom, Rgb middle, Rgb top) {

    /**
     * A colour, each channel 0 to 1.
     */
    public record Rgb(float red, float green, float blue) {

        public static Rgb of(int rgb) {
            return new Rgb(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        }

        /**
         * Brighter (positive) or darker (negative): each channel changed by the fraction {@code percent} of itself.
         */
        public Rgb luminance(float percent) {
            return new Rgb(scale(this.red, percent), scale(this.green, percent), scale(this.blue, percent));
        }

        private static float scale(float channel, float percent) {
            return Math.clamp(channel + channel * percent, 0F, 1F);
        }
    }

    // The colours the original palettes were made from
    private static final Rgb RED = Rgb.of(0xFF0000);
    private static final Rgb YELLOW = Rgb.of(0xFFFF00);
    private static final Rgb LIGHT_GREEN = Rgb.of(0x7FFF00);
    private static final Rgb GREEN = Rgb.of(0x00FF00);
    private static final Rgb TURQUOISE = Rgb.of(0x00FF7F);
    private static final Rgb CYAN = Rgb.of(0x00FFFF);
    private static final Rgb BLUE = Rgb.of(0x0000FF);
    private static final Rgb MAGENTA = Rgb.of(0xFF00FF);
    private static final Rgb INDIGO = Rgb.of(0x4B0082);
    private static final Rgb NAVY = Rgb.of(0x000080);
    private static final Rgb AURORA_RED = new Rgb(1F, 0F, 0F);
    private static final Rgb AURORA_GREEN = new Rgb(0.5F, 1F, 0F);
    private static final Rgb AURORA_BLUE = new Rgb(0F, 0.8F, 1F);

    private static final float WARMER = 0.3F;
    private static final float COOLER = -0.3F;

    public static final List<AuroraPalette> PALETTES;

    static {
        var palettes = new ArrayList<AuroraPalette>();
        palettes.add(of(Rgb.of(0x00FF99), Rgb.of(0x33FF00)));
        palettes.add(of(BLUE, GREEN));
        palettes.add(of(MAGENTA, GREEN));
        palettes.add(of(INDIGO, GREEN));
        palettes.add(of(TURQUOISE, LIGHT_GREEN));
        palettes.add(of(YELLOW, RED));
        palettes.add(of(GREEN, RED));
        palettes.add(of(GREEN, YELLOW));
        palettes.add(of(RED, YELLOW));
        palettes.add(of(NAVY, INDIGO));
        palettes.add(of(CYAN, MAGENTA));
        palettes.add(new AuroraPalette(AURORA_GREEN, AURORA_BLUE, AURORA_RED));

        // Warmer and cooler versions of some of them
        var varied = List.of(
                of(YELLOW, RED),
                of(GREEN, RED),
                of(GREEN, YELLOW),
                of(BLUE, GREEN),
                of(INDIGO, GREEN),
                new AuroraPalette(AURORA_GREEN, AURORA_BLUE, AURORA_RED));
        for (var palette : varied)
            palettes.add(palette.luminance(WARMER));
        for (var palette : varied)
            palettes.add(palette.luminance(COOLER));

        // Brightening an already saturated colour set changes nothing, so a few of the warmer versions are the same as
        // the originals; drop those, rather than have them make their originals twice as likely
        PALETTES = palettes.stream().distinct().toList();
    }

    /**
     * A palette that blends from {@code base} at the bottom to {@code fade} at the top; the middle is the base colour,
     * as in the original.
     */
    private static AuroraPalette of(Rgb base, Rgb fade) {
        return new AuroraPalette(base, base, fade);
    }

    public AuroraPalette luminance(float percent) {
        return new AuroraPalette(this.bottom.luminance(percent), this.middle.luminance(percent), this.top.luminance(percent));
    }
}
