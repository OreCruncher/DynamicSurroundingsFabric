package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;

@Mixin(ParticleManager.class)
public interface MixinParticleManager {

    @Accessor("PARTICLE_TEXTURE_SHEETS")
    static List<ParticleTextureSheet> getParticleTextureSheets() { return null; }

    @Accessor("PARTICLE_TEXTURE_SHEETS")
    @Mutable
    static void setParticleTextureSheets(List<ParticleTextureSheet> sheets) { }

    @Accessor("spriteAwareFactories")
    Map<Identifier, SpriteProvider> getSpriteAwareFactories();

    @Invoker("createParticle")
    <T extends ParticleEffect> Particle dsurroundCreateParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);
}
