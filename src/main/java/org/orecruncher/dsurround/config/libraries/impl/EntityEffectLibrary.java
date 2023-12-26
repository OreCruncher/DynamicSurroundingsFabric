package org.orecruncher.dsurround.config.libraries.impl;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.tags.EntityEffectTags;
import org.orecruncher.dsurround.tags.TagHelpers;
import org.orecruncher.dsurround.xface.ILivingEntityExtended;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EntityEffectLibrary implements IEntityEffectLibrary {

    private final IModLog logger;
    private final Reference2ObjectOpenHashMap<EntityType<?>, Set<EntityEffectType>> entityEffects = new Reference2ObjectOpenHashMap<>();
    private EntityEffectInfo defaultInfo;
    private int version;

    public EntityEffectLibrary(IModLog logger) {
        this.logger = logger;
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {
        this.entityEffects.clear();
        this.version++;

        this.defaultInfo = new EntityEffectInfo(this.version, null, ImmutableList.of()) {
            @Override
            public boolean isDefault() {
                return true;
            }
            @Override
            public void activate() {}
            @Override
            public void deactivate() {}
            @Override
            public void tick() {}
            @Override
            public boolean isAlive()
            {
                return true;
            }
            @Override
            public boolean isVisibleTo(PlayerEntity player) {
                return false;
            }
            @Override
            public boolean isWithinDistance(LivingEntity entity, int distance) {
                throw new RuntimeException("Should not be invoked on DEFAULT EntityEffectInfo");
            }
        };

        this.logger.info("Entity effects configured; version is now %d", this.version);
    }

    @Override
    public Stream<String> dump() {
        return Stream.of();
    }

    @Override
    public boolean doesEntityEffectInfoExist(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        return accessor.dsurround_getEffectInfo() != null;
    }

    @Override
    public void clearEntityEffectInfo(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        accessor.dsurround_setEffectInfo(null);
    }

    @Override
    public EntityEffectInfo getEntityEffectInfo(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        var info = accessor.dsurround_getEffectInfo();

        if (info != null && info.getVersion() == this.version)
            return info;

        // Going to initialize a new one.  Deactivate the existing manager.
        if (info != null) {
            info.deactivate();
        }

        // Find the entity in our map
        var types = this.entityEffects.computeIfAbsent(entity.getType(), EntityEffectLibrary::gatherEffectsFromConfigRules);

        // Project the effect instances
        var effects = types.stream()
                .map(e -> e.produce(entity))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(ReferenceOpenHashSet::new));

        // If we have effect instances create a new info object.  Otherwise, set
        // the default.
        if (!effects.isEmpty())
            info = new EntityEffectInfo(this.version, entity, effects);
        else
            info = this.defaultInfo;

        accessor.dsurround_setEffectInfo(info);

        // Initialize the attached effects before returning.  Usually the next step in processing would be
        // to tick the effects.
        info.activate();

        return info;
    }

    private static Set<EntityEffectType> gatherEffectsFromConfigRules(EntityType<?> entityType) {
        // Gather all the effect types that apply to the entity
        Set<EntityEffectType> effectTypes = new ReferenceOpenHashSet<>();

        if (TagHelpers.isIn(EntityEffectTags.BOW_PULL, entityType))
            effectTypes.add(EntityEffectType.BOW_PULL);
        if (TagHelpers.isIn(EntityEffectTags.FROST_BREATH, entityType))
            effectTypes.add(EntityEffectType.FROST_BREATH);
        if (TagHelpers.isIn(EntityEffectTags.ITEM_SWING, entityType))
            effectTypes.add(EntityEffectType.ITEM_SWING);
        if (TagHelpers.isIn(EntityEffectTags.TOOLBAR, entityType))
            effectTypes.add(EntityEffectType.PLAYER_TOOLBAR);
        if (TagHelpers.isIn(EntityEffectTags.BRUSH_STEP, entityType))
            effectTypes.add(EntityEffectType.BRUSH_STEP);

        return effectTypes;
    }
}
