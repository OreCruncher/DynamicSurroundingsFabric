package org.orecruncher.dsurround.effects.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;

import java.util.Random;

@Environment(EnvType.CLIENT)
public final class ParticleUtils {

    private static final Random RANDOM = XorShiftRandom.current();

    public static SpriteProvider getSpriteProvider(ParticleType<?> particleType) {
        var id = Registries.PARTICLE_TYPE.getId(particleType);
        return ((MixinParticleManager) GameUtils.getParticleManager()).getSpriteAwareFactories().get(id);
    }

    public static Vec3d getBreathOrigin(final LivingEntity entity) {
        final Vec3d eyePosition = eyePosition(entity).subtract(0D, entity.isBaby() ? 0.1D : 0.2D, 0D);
        final Vec3d look = entity.getRotationVec(1F); // Don't use the other look vector method!
        return eyePosition.add(look.multiply(entity.isBaby() ? 0.25D : 0.5D));
    }

    public static Vec3d getLookTrajectory(final LivingEntity entity) {
        return entity.getRotationVec(1F)
                .rotateZ(RANDOM.nextFloat() * 2F)   // yaw
                .rotateY(RANDOM.nextFloat() * 2F)   // pitch
                .normalize();
    }

    /*
     * Use some corrective lenses because the MC routine just doesn't lower the
     * height enough for our rendering purpose.
     */
    private static Vec3d eyePosition(final Entity e) {
        var y = e.getEyePos();
        if (e.isSneaking()) {
            y = y.subtract(0, 0.25D, 0);
        }
        return y;
    }
}