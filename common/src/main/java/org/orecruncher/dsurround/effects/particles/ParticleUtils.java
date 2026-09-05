package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Optional;

public final class ParticleUtils {

    private static final IRandomizer RANDOM = Randomizer.current();

    public static SpriteSet getSpriteProvider(ParticleType<?> particleType) {

        var registered = DSurroundParticleSpriteSets.get(particleType);
        if (registered.isPresent())
            return registered.get();

        var spriteSet = getSpriteProviderFromEngine(particleType);
        if (spriteSet.isPresent()) {
            DSurroundParticleSpriteSets.register(particleType, spriteSet.get());
            return spriteSet.get();
        }

        return null;
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

    private static Optional<SpriteSet> getSpriteProviderFromEngine(ParticleType<?> particleType) {
        var id = getParticleId(particleType);
        if (id.isPresent()) {
            var engineSpriteSets = GameUtils.getParticleManager().spriteSets;
            return Optional.ofNullable(engineSpriteSets.get(id.get()));
        }

        return Optional.empty();
    }

    private static Optional<ResourceLocation> getParticleId(ParticleType<?> particleType) {
        return RegistryUtils.getRegistry(Registries.PARTICLE_TYPE)
                .flatMap(registry -> registry.getResourceKey(particleType).map(ResourceKey::location));
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

    public static void register() {
    }
}