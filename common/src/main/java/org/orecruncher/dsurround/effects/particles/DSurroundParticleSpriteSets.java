package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class DSurroundParticleSpriteSets {

    private static final Map<ParticleType<?>, SpriteSet> BY_TYPE = new IdentityHashMap<>(12);

    private DSurroundParticleSpriteSets() {
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