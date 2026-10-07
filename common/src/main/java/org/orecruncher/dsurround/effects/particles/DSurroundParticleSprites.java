package org.orecruncher.dsurround.effects.particles;

import org.orecruncher.dsurround.config.WaterRippleStyle;

import java.util.List;

/**
 * Sprites for the particles the mod creates itself, rather than through the particle engine. Being a client side
 * mod, it doesn't register particle types.
 */
public final class DSurroundParticleSprites {

    public static final AtlasSpriteSet WATER_RIPPLE = AtlasSpriteSet.of("ripple", "ripple1", "ripple2");
    public static final AtlasSpriteSet WATER_RIPPLE_PIXELATED = AtlasSpriteSet.of(
            "water_ripple_pixelated_0", "water_ripple_pixelated_1", "water_ripple_pixelated_2",
            "water_ripple_pixelated_3", "water_ripple_pixelated_4", "water_ripple_pixelated_5",
            "water_ripple_pixelated_6");
    public static final AtlasSpriteSet WATERFALL_MIST = AtlasSpriteSet.of(
            "waterfall_mist_0", "waterfall_mist_1", "waterfall_mist_2",
            "waterfall_mist_3", "waterfall_mist_4", "waterfall_mist_5");
    public static final AtlasSpriteSet WATER_FOAM = AtlasSpriteSet.of(
            "water_foam_0", "water_foam_1", "water_foam_2",
            "water_foam_3", "water_foam_4", "water_foam_5");
    public static final AtlasSpriteSet FIREFLY_GLOW = AtlasSpriteSet.of("firefly_glow");

    static final List<AtlasSpriteSet> ALL = List.of(WATER_RIPPLE, WATER_RIPPLE_PIXELATED, WATERFALL_MIST, WATER_FOAM, FIREFLY_GLOW);

    private DSurroundParticleSprites() {
    }

    public static AtlasSpriteSet forRippleStyle(WaterRippleStyle style) {
        return style == WaterRippleStyle.PIXELATED_CIRCLE
                ? WATER_RIPPLE_PIXELATED
                : WATER_RIPPLE;
    }
}
