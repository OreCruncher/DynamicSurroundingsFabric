package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public final class ParticleUtils {

    private static final IRandomizer RANDOM = Randomizer.current();

    /**
     * The sprites the particle engine loaded for a game particle. The engine keeps one set per particle type and
     * swaps its sprites when resources reload, so it can be held on to.
     */
    public static SpriteSet getSpriteProvider(ParticleType<?> particleType) {
        var id = BuiltInRegistries.PARTICLE_TYPE.getKey(particleType);
        return GameUtils.getParticleResources().spriteSets.get(id);
    }

    public static Vec3 getBreathOrigin(final LivingEntity entity) {
        final Vec3 eyePosition = eyePosition(entity).subtract(0D, entity.isBaby() ? 0.1D : 0.2D, 0D);
        final Vec3 look = entity.getViewVector(1F); // Don't use the other look vector method!
        return eyePosition.add(look.scale(entity.isBaby() ? 0.25D : 0.5D));
    }

    public static Vec3 getLookTrajectory(final LivingEntity entity) {
        return entity.getLookAngle()
                .zRot(RANDOM.nextFloat() * 2F)   // yaw
                .yRot(RANDOM.nextFloat() * 2F)   // pitch
                .normalize();
    }

    public static <T extends ParticleOptions> Particle createParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        return GameUtils.getParticleManager().createParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }

    /*
     * Use some corrective lenses because the MC routine just doesn't lower the
     * height enough for our rendering purpose.
     */
    private static Vec3 eyePosition(final Entity e) {
        var y = e.getEyePosition();
        if (e.isCrouching()) {
            y = y.subtract(0, 0.25D, 0);
        }
        return y;
    }
}