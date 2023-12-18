package org.orecruncher.dsurround.config.libraries.impl;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.tags.EntityEffectTags;
import org.orecruncher.dsurround.xface.ILivingEntityExtended;

import java.util.Set;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class EntityEffectLibrary implements IEntityEffectLibrary {

    private final IModLog logger;
    private final Reference2ObjectMap<EntityType<?>, Set<EntityEffectType>> entityEffects = new Reference2ObjectOpenHashMap<>();
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

        this.logger.info("Entity config rules configured; version is now %d", version);
    }

    @Override
    public Stream<String> dump() {
        return Stream.of();
    }

    @Override
    public boolean doesEntityEffectInfoExist(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        return accessor.getEffectInfo() != null;
    }

    @Override
    public void clearEntityEffectInfo(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        accessor.setEffectInfo(null);
    }

    @Override
    public EntityEffectInfo getEntityEffectInfo(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        var info = accessor.getEffectInfo();

        if (info != null && info.getVersion() == this.version)
            return info;

        // Going to initialize a new one.  Deactivate the existing manager.
        if (info != null) {
            info.deactivate();
            info = null;
        }

        // Find the entity in our map
        var types = this.entityEffects.get(entity.getType());
        if (types == null) {
            // Didn't find it.  Gather from the rules and cache
            types = gatherEffectsFromConfigRules(entity);
            this.entityEffects.put(entity.getType(), types);
        }

        // Project the effect instances
        Set<IEntityEffect> effects = new ReferenceOpenHashSet<>();
        for (var type : types) {
            var effectsToApply = type.produce(entity);
            effects.addAll(effectsToApply);
        }

        // If we have effect instances create a new info object.  Otherwise, set
        // the default.
        if (!effects.isEmpty())
            info = new EntityEffectInfo(this.version, entity, effects);
        else
            info = this.defaultInfo;

        accessor.setEffectInfo(info);

        // Initialize the attached effects before returning.  Usually the next step in processing would be
        // to tick the effects.
        info.activate();

        return info;
    }

    private static Set<EntityEffectType> gatherEffectsFromConfigRules(LivingEntity entity) {
        // Gather all the effect types that apply to the entity
        Set<EntityEffectType> effectTypes = new ReferenceOpenHashSet<>();

        var entityType = entity.getType();
        if (entityType.isIn(EntityEffectTags.BOW_PULL))
            effectTypes.add(EntityEffectType.BOW_PULL);
        if (entityType.isIn(EntityEffectTags.FROST_BREATH))
            effectTypes.add(EntityEffectType.FROST_BREATH);
        if (entityType.isIn(EntityEffectTags.ITEM_SWING))
            effectTypes.add(EntityEffectType.ITEM_SWING);
        if (entityType.isIn(EntityEffectTags.TOOLBAR))
            effectTypes.add(EntityEffectType.PLAYER_TOOLBAR);

        return effectTypes;
    }
}
