package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.*;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum EntityEffectType {
    UNKNOWN("unknown", entity -> null, () -> false),
    BOW_PULL("bow_pull", entity -> getInstance(BowUseEffect.class), () -> Client.Config.entityEffects.enableBowPull),
    FROST_BREATH("frost_breath", entity -> getInstance(BreathEffect.class), () -> Client.Config.entityEffects.enableBreathEffect),
    PLAYER_TOOLBAR("player_toolbar", entity -> getInstance(ToolbarEffect.class), () -> Client.Config.entityEffects.enablePlayerToolbarEffect),
    ITEM_SWING("item_swing", entity -> getInstance(ItemSwingEffect.class), () -> Client.Config.entityEffects.enableSwingEffect),
    BRUSH_STEP("brush_step", entity -> getInstance(StepThroughBrushEffect.class), () -> Client.Config.entityEffects.enableBrushStepEffect);

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

    public Optional<IEntityEffect> produce(LivingEntity entity) {
        if (this.isEnabled()) {
            return Optional.ofNullable(this.factory.apply(entity));
        }
        return Optional.empty();
    }

    public static EntityEffectType byName(String name) {
        return BY_NAME.get(name);
    }
}
