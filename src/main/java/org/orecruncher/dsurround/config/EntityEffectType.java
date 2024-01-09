package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.*;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EntityEffectType {
    UNKNOWN("unknown", entity -> null),
    BOW_PULL("bow_pull", entity -> getInstance(BowUseEffect.class)),
    FROST_BREATH("frost_breath", entity -> getInstance(BreathEffect.class)),
    PLAYER_TOOLBAR("player_toolbar", entity -> getInstance(ToolbarEffect.class)),
    ITEM_SWING("item_swing", entity -> getInstance(ItemSwingEffect.class)),
    BRUSH_STEP("brush_step", entity -> getInstance(StepThroughBrushEffect.class));

    private static IEntityEffect getInstance(Class<? extends IEntityEffect> clazz) {
        return ContainerManager.resolve(clazz);
    }

    private static final Map<String, EntityEffectType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(EntityEffectType::getName, (category) -> category));
    public static final Codec<EntityEffectType> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown entity effect"), d -> d.name);

    private final String name;
    private final Function<LivingEntity, IEntityEffect> factory;
    private BooleanSupplier enabled;

    EntityEffectType(String name, Function<LivingEntity, IEntityEffect> factory) {
        this.name = name;
        this.factory = factory;
    }

    public String getName() {
        return this.name;
    }

    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
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

    private void setConfigProvider(BooleanSupplier supplier) {
        this.enabled = supplier;
    }

    static {
        Configuration.EntityEffects config = ContainerManager.resolve(Configuration.EntityEffects.class);
        UNKNOWN.setConfigProvider(() -> false);
        BOW_PULL.setConfigProvider(() -> config.enableBowPull);
        FROST_BREATH.setConfigProvider(() -> config.enableBreathEffect);
        PLAYER_TOOLBAR.setConfigProvider(() -> config.enablePlayerToolbarEffect);
        ITEM_SWING.setConfigProvider(() -> config.enableSwingEffect);
        BRUSH_STEP.setConfigProvider(() -> config.enableBrushStepEffect && !Library.getPlatform().isModLoaded(Constants.MOD_PRESENCE_FOOTSTEPS));
    }

}
