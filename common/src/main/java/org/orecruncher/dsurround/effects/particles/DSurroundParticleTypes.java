package org.orecruncher.dsurround.effects.particles;

import net.minecraft.core.particles.SimpleParticleType;
import org.orecruncher.dsurround.config.WaterRippleStyle;

public final class DSurroundParticleTypes {

    // These fields will be populated by a mixin during client construction
    public static SimpleParticleType WATER_RIPPLE;
    public static SimpleParticleType WATER_RIPPLE_PIXELATED;
    // Sprite sets for particles the mod creates itself, rather than through the particle engine
    public static SimpleParticleType WATERFALL_MIST;
    public static SimpleParticleType WATER_FOAM;
    public static SimpleParticleType FIREFLY_GLOW;

    public static SimpleParticleType forRippleStyle(WaterRippleStyle style) {
        return style == WaterRippleStyle.PIXELATED_CIRCLE
                ? WATER_RIPPLE_PIXELATED
                : WATER_RIPPLE;
    }
}