package org.orecruncher.dsurround.effects.particles;

import net.minecraft.core.particles.SimpleParticleType;
import org.orecruncher.dsurround.config.WaterRippleStyle;

public final class DSurroundParticleTypes {

    // These fields will be populated by a mixin during client construction
    public static SimpleParticleType WATER_RIPPLE;
    public static SimpleParticleType WATER_RIPPLE_PIXELATED;
    // No longer a particle of its own (the waterfall cascade was replaced by mist): kept as the source of the cloud
    // sprites (textures/particle/cascade_0 to 5) that waterfall mist and water foam are drawn with
    public static SimpleParticleType WATERFALL_CASCADE;

    public static SimpleParticleType forRippleStyle(WaterRippleStyle style) {
        return style == WaterRippleStyle.PIXELATED_CIRCLE
                ? WATER_RIPPLE_PIXELATED
                : WATER_RIPPLE;
    }
}