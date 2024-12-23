package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.tags.EntityEffectTags;
import org.orecruncher.dsurround.mixinutils.ILivingEntityExtended;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EntityEffectLibrary implements IEntityEffectLibrary {

    private final ITagLibrary tagLibrary;
    private final IModLog logger;
    private final Reference2ObjectOpenHashMap<EntityType<?>, Set<EntityEffectType>> entityEffects = new Reference2ObjectOpenHashMap<>();
    private EntityEffectInfo defaultInfo;
    private int version;

    public EntityEffectLibrary(ITagLibrary tagLibrary, IModLog logger) {
        this.tagLibrary = tagLibrary;
        this.logger = ModLog.createChild(logger, "EntityEffectLibrary");
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {

        this.version++;

        if (scope == IReloadEvent.Scope.TAGS) {
            this.logger.info("[EntityEffectLibrary] received tag update notification; version is now %d", this.version);
            return;
        }

        this.entityEffects.clear();
        this.defaultInfo = EntityEffectInfo.createDefault(this.version);
        this.logger.info("[EntityEffectLibrary] Configured; version is now %d", this.version);
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
        var types = this.entityEffects.computeIfAbsent(entity.getType(), this::gatherEffectsFromConfigRules);

        // Project the effect instances
        var effects = types.stream()
                .map(e -> e.produce(entity))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // If we have effect instances create a new info object.  Otherwise, set
        // the default.
        if (!effects.isEmpty())
            info = new EntityEffectInfo(this.version, entity, effects);
        else
            info = this.defaultInfo;

        accessor.dsurround_setEffectInfo(info);

        // Initialize the attached effects before returning.
        // Usually, the next step in processing would be
        // to tick the effects.
        info.activate();

        return info;
    }

    private Set<EntityEffectType> gatherEffectsFromConfigRules(EntityType<?> entityType) {
        // Gather all the effect types that apply to the entity
        Set<EntityEffectType> effectTypes = new ReferenceOpenHashSet<>();

        if (this.tagLibrary.is(EntityEffectTags.BOW_PULL, entityType))
            effectTypes.add(EntityEffectType.BOW_PULL);
        if (this.tagLibrary.is(EntityEffectTags.FROST_BREATH, entityType))
            effectTypes.add(EntityEffectType.FROST_BREATH);
        if (this.tagLibrary.is(EntityEffectTags.ITEM_SWING, entityType))
            effectTypes.add(EntityEffectType.ITEM_SWING);
        if (this.tagLibrary.is(EntityEffectTags.TOOLBAR, entityType))
            effectTypes.add(EntityEffectType.PLAYER_TOOLBAR);
        if (this.tagLibrary.is(EntityEffectTags.BRUSH_STEP, entityType))
            effectTypes.add(EntityEffectType.BRUSH_STEP);

        return effectTypes;
    }
}
