package org.orecruncher.dsurround.config;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.StringIdentifiable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.producers.BowUseEffectProducer;
import org.orecruncher.dsurround.effects.entity.producers.BreathEffectProducer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public enum EntityEffectType  implements StringIdentifiable {
    UNKNOWN("unknown", entity -> ImmutableList.of(), () -> false),
    BOW_PULL("bow_pull", entity -> new BowUseEffectProducer().produce(entity), () -> Client.Config.entityEffects.enableBowPull),
    FROST_BREATH("frost_breath", entity -> new BreathEffectProducer().produce(entity), () -> Client.Config.entityEffects.enableBreathEffect);

    private static final Map<String, EntityEffectType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(EntityEffectType::getName, (category) -> category));
    public static final Codec<EntityEffectType> CODEC = StringIdentifiable.createCodec(EntityEffectType::values, EntityEffectType::byName);

    private final String name;
    private final Function<LivingEntity, Collection<IEntityEffect>> factory;
    private final Supplier<Boolean> enabled;

    EntityEffectType(String name, Function<LivingEntity, Collection<IEntityEffect>> factory, Supplier<Boolean> enabled) {
        this.name = name;
        this.factory = factory;
        this.enabled = enabled;
    }

    public String getName() {
        return this.name;
    }

    public boolean isEnabled() {
        return this.enabled.get();
    }

    public Collection<IEntityEffect> produce(LivingEntity entity) {
        if (this.isEnabled())
            return this.factory.apply(entity);
        return ImmutableList.of();
    }

    @Override
    public String asString() {
        return this.name;
    }

    public static EntityEffectType byName(String name) {
        return BY_NAME.get(name);
    }
}
