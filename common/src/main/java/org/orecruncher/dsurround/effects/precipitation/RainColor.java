package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.util.Mth;

/**
 * The colour particle rain is drawn in, falling and running down windows: from a nearly colourless pale grey to a
 * deep, clear blue, as the player sets its tint. Lit by the lightmap as it is drawn, so it darkens at night and in
 * shade.
 */
public record RainColor(float red, float green, float blue) {

    /**
     * The tint rain has unless the player changes it: a pale blue-grey, the colour it has always been drawn in
     */
    public static final int DEFAULT_TINT = 15;

    // The colour at the default tint, which rain had before it could be tinted, and at full tint. The colour at no tint
    // follows from them: the default tint lies that share of the way from it to full.
    static final RainColor LONG_STANDING = new RainColor(0.72F, 0.78F, 0.88F);
    static final RainColor BLUE = new RainColor(0.20F, 0.42F, 1.0F);
    static final RainColor NEUTRAL = beforeTint(LONG_STANDING, BLUE, DEFAULT_TINT / 100F);

    public static final RainColor USUAL = ofTint(DEFAULT_TINT);

    /**
     * The rain's colour for a tint
     *
     * @param percent how blue, 0 for nearly colourless to 100 for a deep blue; clamped
     */
    public static RainColor ofTint(int percent) {
        float t = Mth.clamp(percent / 100F, 0F, 1F);
        return new RainColor(Mth.lerp(t, NEUTRAL.red, BLUE.red), Mth.lerp(t, NEUTRAL.green, BLUE.green), Mth.lerp(t, NEUTRAL.blue, BLUE.blue));
    }

    /**
     * The colour at no tint, such that a share of the way from it to {@code full} is {@code at}
     */
    private static RainColor beforeTint(RainColor at, RainColor full, float share) {
        return new RainColor((at.red - share * full.red) / (1F - share), (at.green - share * full.green) / (1F - share),
                (at.blue - share * full.blue) / (1F - share));
    }
}
