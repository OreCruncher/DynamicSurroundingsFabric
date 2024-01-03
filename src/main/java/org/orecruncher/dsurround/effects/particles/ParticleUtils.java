package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;

import java.util.Random;

public final class ParticleUtils {

    private static final Random RANDOM = Randomizer.current();

    public static SpriteSet getSpriteProvider(ParticleType<?> particleType) {
        var id = RegistryUtils.getRegistry(Registries.PARTICLE_TYPE)
                .map(r -> r.getResourceKey(particleType).map(ResourceKey::location))
                .orElseThrow();
        return ((MixinParticleManager) GameUtils.getParticleManager()).dsurround_getSpriteAwareFactories().get(id.get());
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