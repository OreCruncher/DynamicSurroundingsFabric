// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.overlay;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.mixins.core.MixinLivingEntityAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Works out which effects a mob has, for the target portrait. The server sends the client a player's own effects and
 * its mount's, not a mob's; what every entity does send is the particles of its visible effects, so each particle is
 * matched back to its effect: by color for the usual tinted swirl, by type for an effect with a particle of its own.
 * An effect with hidden particles can't be seen, and of two effects of one color, one is taken for both.
 */
final class TargetEffects {

    private static final Int2ObjectOpenHashMap<Holder<MobEffect>> BY_COLOR = new Int2ObjectOpenHashMap<>();
    private static final Map<ParticleType<?>, Holder<MobEffect>> BY_TYPE = new HashMap<>();
    // How many effects were looked at to build them: mods add theirs before a world is joined
    private static int known;

    private TargetEffects() {
    }

    /**
     * The effects showing on a mob, most recent particles first as the server lists them, each once
     */
    static List<Holder<MobEffect>> of(LivingEntity entity) {
        var particles = entity.getEntityData().get(MixinLivingEntityAccessor.dsurround$effectParticles());
        if (particles.isEmpty())
            return List.of();
        index();
        var effects = new LinkedHashSet<Holder<MobEffect>>();
        for (var particle : particles) {
            var effect = find(particle);
            if (effect != null)
                effects.add(effect);
        }
        return new ArrayList<>(effects);
    }

    private static Holder<MobEffect> find(ParticleOptions particle) {
        if (particle instanceof ColorParticleOption color)
            return BY_COLOR.get(rgb(color));
        return BY_TYPE.get(particle.getType());
    }

    private static void index() {
        if (known == BuiltInRegistries.MOB_EFFECT.size())
            return;
        BY_COLOR.clear();
        BY_TYPE.clear();
        // Recorded first: a mod's effect that fails is left out, not looked at again every tick
        known = BuiltInRegistries.MOB_EFFECT.size();
        BuiltInRegistries.MOB_EFFECT.listElements().forEach(effect -> {
            ParticleOptions particle;
            try {
                particle = effect.value().createParticleOptions(new MobEffectInstance(effect));
            } catch (RuntimeException e) {
                // A mod's effect that needs more than a plain instance to make its particle: not matched
                return;
            }
            if (particle instanceof ColorParticleOption color)
                BY_COLOR.putIfAbsent(rgb(color), effect);
            else if (particle != null)
                BY_TYPE.putIfAbsent(particle.getType(), effect);
        });
    }

    /**
     * A particle's color, without its alpha, which differs for a beacon's effects
     */
    private static int rgb(ColorParticleOption color) {
        return Math.round(color.getRed() * 255F) << 16 | Math.round(color.getGreen() * 255F) << 8 | Math.round(color.getBlue() * 255F);
    }
}
