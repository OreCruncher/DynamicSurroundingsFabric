package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.particle.Particle;
import org.orecruncher.dsurround.mixinutils.IWindBlown;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.weather.SceneWind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Carries wind blown particles downwind. The drift is added to how far the particle moves, not to its speed: a
 * vanilla particle's drag can't be relied on (campfire smoke has none), and going through its move keeps it stopping
 * at walls. Particles that place themselves rather than move (portals, enchanting glyphs) aren't touched.
 */
@Mixin(Particle.class)
public abstract class MixinParticle implements IWindBlown {

    @Shadow
    protected double x;
    @Shadow
    protected double z;

    // 0 for particles the wind doesn't move: all but those marked
    @Unique
    private float dsurround$windExposure;
    @Unique
    private float dsurround$driftX;
    @Unique
    private float dsurround$driftZ;

    @Override
    public void dsurround$blowInWind(float exposure) {
        this.dsurround$windExposure = exposure;
    }

    @ModifyVariable(method = "move(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private double dsurround$driftX(double dx) {
        if (this.dsurround$windExposure == 0F)
            return dx;
        // Every wind blown particle, every tick: no lambda
        try {
            this.dsurround$driftX = SceneWind.drift(this.dsurround$driftX, SceneWind.x(this.x, this.z), this.dsurround$windExposure);
            return dx + this.dsurround$driftX;
        } catch (Throwable t) {
            MixinHelpers.WIND_DRIFT_GUARD.failed(t);
            return dx;
        }
    }

    @ModifyVariable(method = "move(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private double dsurround$driftZ(double dz) {
        if (this.dsurround$windExposure == 0F)
            return dz;
        try {
            this.dsurround$driftZ = SceneWind.drift(this.dsurround$driftZ, SceneWind.z(this.x, this.z), this.dsurround$windExposure);
            return dz + this.dsurround$driftZ;
        } catch (Throwable t) {
            MixinHelpers.WIND_DRIFT_GUARD.failed(t);
            return dz;
        }
    }
}
