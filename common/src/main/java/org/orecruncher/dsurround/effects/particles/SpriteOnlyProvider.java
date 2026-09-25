package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Special sprite provider that captures the sprite information in order to store it into a
 * map for later retrieval.
 */
public final class SpriteOnlyProvider implements ParticleProvider<SimpleParticleType> {
    public SpriteOnlyProvider(SimpleParticleType particleType, SpriteSet spriteSet, RandomSource random) {
        DSurroundParticleSpriteSets.register(particleType, spriteSet);
    }

    @Override
    public @Nullable Particle createParticle(@NotNull SimpleParticleType particleOptions, @NotNull ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource random) {
        return null;
    }
}