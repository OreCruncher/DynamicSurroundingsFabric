package org.orecruncher.dsurround.config;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.BowUseEffect;
import org.orecruncher.dsurround.effects.entity.BreathEffect;
import org.orecruncher.dsurround.effects.entity.ItemSwingEffect;
import org.orecruncher.dsurround.effects.entity.ToolbarEffect;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public enum EntityEffectType {
    UNKNOWN("unknown", entity -> null, () -> false),
    BOW_PULL("bow_pull", entity -> getInstance(BowUseEffect.class), () -> Client.Config.entityEffects.enableBowPull),
    FROST_BREATH("frost_breath", entity -> getInstance(BreathEffect.class), () -> Client.Config.entityEffects.enableBreathEffect),
    PLAYER_TOOLBAR("player_toolbar", entity -> getInstance(ToolbarEffect.class), () -> Client.Config.entityEffects.enablePlayerToolbarEffect),
    ITEM_SWING("item_swing", entity -> getInstance(ItemSwingEffect.class), () -> Client.Config.entityEffects.enableSwingEffect);

    private static IEntityEffect getInstance(Class<? extends IEntityEffect> clazz) {
        return ContainerManager.resolve(clazz);
    }

    private static final Map<String, EntityEffectType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(EntityEffectType::getName, (category) -> category));
    public static final Codec<EntityEffectType> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown entity effect"), d -> d.name);

    private final String name;
    private final Function<LivingEntity, IEntityEffect> factory;
    private final Supplier<Boolean> enabled;

    EntityEffectType(String name, Function<LivingEntity, IEntityEffect> factory, Supplier<Boolean> enabled) {
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
        if (this.isEnabled()) {
            var effect = this.factory.apply(entity);
            if (effect != null)
                return ImmutableList.of(effect);
        }
        return ImmutableList.of();
    }

    public static EntityEffectType byName(String name) {
        return BY_NAME.get(name);
    }
}
