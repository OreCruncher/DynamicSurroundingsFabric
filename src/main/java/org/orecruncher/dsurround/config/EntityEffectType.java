package org.orecruncher.dsurround.config;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.StringIdentifiable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.producers.BowUseEffectProducer;
import org.orecruncher.dsurround.effects.entity.producers.BreathEffectProducer;
import org.orecruncher.dsurround.effects.entity.producers.ItemSwingEffectProducer;
import org.orecruncher.dsurround.effects.entity.producers.ToolbarEffectProducer;

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
    FROST_BREATH("frost_breath", entity -> new BreathEffectProducer().produce(entity), () -> Client.Config.entityEffects.enableBreathEffect),
    PLAYER_TOOLBAR("player_toolbar", entity -> new ToolbarEffectProducer().produce(entity), () -> Client.Config.entityEffects.enablePlayerToolbarEffect),
    ITEM_SWING("item_swing", entity -> new ItemSwingEffectProducer().produce(entity), () -> Client.Config.entityEffects.enableSwingEffect);

    private static final Map<String, EntityEffectType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(EntityEffectType::getName, (category) -> category));
    public static final Codec<EntityEffectType> CODEC = StringIdentifiable.createCodec(EntityEffectType::values);

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
