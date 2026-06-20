package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loader-neutral cache for particle sprite sets captured from client particle provider registration.
 *
 * <p>Minecraft owns the actual SpriteSet instances inside {@code ParticleEngine}.  Accessing that
 * private map with a mixin accessor is fragile across the 26.x no-remap snapshots, so platform code
 * can register the SpriteSet it receives from the official particle provider registration path here.</p>
 */
public final class DsurroundParticleSpriteSets {

    private static final Map<ParticleType<?>, SpriteSet> BY_TYPE = new ConcurrentHashMap<>();

    private DsurroundParticleSpriteSets() {
    }

    public static void register(ParticleType<?> particleType, SpriteSet spriteSet) {
        if (particleType != null && spriteSet != null) {
            BY_TYPE.put(particleType, spriteSet);
        }
    }

    public static Optional<SpriteSet> get(ParticleType<?> particleType) {
        return Optional.ofNullable(BY_TYPE.get(particleType));
    }
}
