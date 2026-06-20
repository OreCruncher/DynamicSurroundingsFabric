package org.orecruncher.dsurround.effects.particles;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.WaterRippleStyle;

public final class DsurroundParticleTypes {

    public static final SimpleParticleType WATER_RIPPLE = register("water_ripple", false);
    public static final SimpleParticleType WATER_RIPPLE_PIXELATED = register("water_ripple_pixelated", false);

    private DsurroundParticleTypes() {
    }

    public static void initialize() {
        // Force static registration before client resources are reloaded.
    }

    public static SimpleParticleType forRippleStyle(WaterRippleStyle style) {
        return style == WaterRippleStyle.PIXELATED_CIRCLE
                ? WATER_RIPPLE_PIXELATED
                : WATER_RIPPLE;
    }

    private static SimpleParticleType register(String name, boolean overrideLimiter) {
        return Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, name),
                new SimpleParticleType(overrideLimiter) {});
    }
}
