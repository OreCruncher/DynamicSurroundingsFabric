package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ParticleManager.class)
public interface MixinParticleManager {

    @Accessor("spriteAwareFactories")
    Map<Identifier, SpriteProvider> getSpriteAwareFactories();

    @Invoker("createParticle")
    <T extends ParticleEffect> Particle dsurroundCreateParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);
}
