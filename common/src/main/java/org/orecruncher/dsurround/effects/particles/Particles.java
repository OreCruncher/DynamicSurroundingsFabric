package org.orecruncher.dsurround.effects.particles;

import dev.architectury.registry.client.particle.ParticleProviderRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.Constants;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class Particles {

    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Constants.MOD_ID, Registries.PARTICLE_TYPE);
    public static final RegistrySupplier<SimpleParticleType> WATER_RIPPLE_PIXELATED = PARTICLE_TYPES.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "pixelated_rain_ripple"), () -> new SimpleParticleType(false));

    public static void register() {
        ParticleProviderRegistry.register(WATER_RIPPLE_PIXELATED, WaterRippleParticle.Provider::new);
    }

    public static void addSpriteSheets(Consumer<ResourceLocation> adder) {
        adder.accept(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "pixelated_ripples"));
    }
}
