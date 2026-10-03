package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.eventing.IConfigChangedEvent;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.tags.EntityEffectTags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Works out which entity effects apply to an entity, and caches the result:
 * <ul>
 *   <li>per entity type, the effect types its tags give it</li>
 *   <li>per entity (by id), the live effects, as an {@link EntityEffectInfo}. Entities with none share one default
 *       instance.</li>
 * </ul>
 * Cached entity info is rebuilt when its version no longer matches, which happens after a reload or a config
 * change.
 */
public class EntityEffectLibrary implements IEntityEffectLibrary {

    private final ITagLibrary tagLibrary;
    private final IModLog logger;

    private final Reference2ObjectOpenHashMap<EntityType<?>, Set<EntityEffectType>> entityEffects = new Reference2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<EntityEffectInfo> entityInfoCache = new Int2ObjectOpenHashMap<>(128);
    // Reused by cleanCache()
    private final IntArrayList toRemove = new IntArrayList();

    // An effect type that fails to produce for an entity is reported once per reload, not once per entity per tick
    private final LogThrottle<EntityEffectType> produceFailures;

    private EntityEffectInfo defaultInfo;
    private int version;

    public EntityEffectLibrary(ITagLibrary tagLibrary, IModLog logger) {
        this.tagLibrary = tagLibrary;
        this.logger = ModLog.createChild(logger, "EntityEffectLibrary");
        this.produceFailures = LogThrottle.oncePerKey(this.logger, "entity effect failures", "the next reload");
        this.defaultInfo = EntityEffectInfo.createDefault(this.version);

        // Whether an effect type is produced depends on config, so cached entity info is rebuilt when it changes
        IConfigChangedEvent.EVENT.register(cfg -> this.invalidate());
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        // Every scope invalidates: tags decide the effect types, so a tag sync matters as much as a full reload
        this.invalidate();
        if (scope == IReloadEvent.Scope.TAGS)
            this.logger.info("received tag update notification; version is now %d", this.version);
        else
            this.logger.info("Configured; version is now %d", this.version);
    }

    /**
     * Makes all cached entity info stale, so it is rebuilt from current tags and config the next time it is used.
     * The per-type cache is cleared, and a new default instance carries the new version: if the default kept an old
     * one, every entity using it would fail the version check and be rebuilt on every tick.
     */
    private void invalidate() {
        this.version++;
        this.produceFailures.reset();
        this.entityEffects.clear();
        this.defaultInfo = EntityEffectInfo.createDefault(this.version);
    }

    /**
     * Each entity type that has effects, with its effect types.
     */
    @Override
    public Stream<String> dump() {
        return BuiltInRegistries.ENTITY_TYPE.stream()
                .map(type -> {
                    var types = this.getEntityEffectTypes(type);
                    if (types.isEmpty())
                        return null;
                    var names = types.stream().map(EntityEffectType::getName).sorted().collect(Collectors.joining(", "));
                    return BuiltInRegistries.ENTITY_TYPE.getKey(type) + ": " + names;
                })
                .filter(Objects::nonNull)
                .sorted();
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public boolean doesEntityEffectInfoExist(LivingEntity entity) {
        return this.entityInfoCache.containsKey(entity.getId());
    }

    @Override
    public void cleanCache(AbstractIntSet entitiesToRetain) {
        for (var kvp : this.entityInfoCache.int2ObjectEntrySet()) {
            if (!entitiesToRetain.contains(kvp.getIntKey())) {
                kvp.getValue().deactivate();
                this.toRemove.add(kvp.getIntKey());
            }
        }

        // Removed after iterating rather than during, to stay clear of modifying the map mid-iteration
        for (int i = 0; i < this.toRemove.size(); i++)
            this.entityInfoCache.remove(this.toRemove.getInt(i));
        this.toRemove.clear();
    }

    @Override
    public void clearCache() {
        for (var info : this.entityInfoCache.values())
            info.deactivate();
        this.entityInfoCache.clear();
    }

    @Override
    public EntityEffectInfo getEntityEffectInfo(LivingEntity entity) {
        var info = this.entityInfoCache.get(entity.getId());

        if (info != null && info.getVersion() == this.version)
            return info;

        // Going to initialize a new one.  Deactivate the existing manager.
        if (info != null) {
            info.deactivate();
        }

        // Project the effect instances. An effect type that throws is reported and left out; the others still apply.
        var effects = new ArrayList<IEntityEffect>();
        RuleGuard.forEach(this.getEntityEffectTypes(entity.getType()),
                type -> type.produce(entity).ifPresent(effects::add),
                (type, t) -> this.produceFailures.error(type, t, "Unable to create effect %s for %s", type.getName(), entity.getType()));

        // If we have effect instances create a new info object.  Otherwise, set
        // the default.
        if (!effects.isEmpty())
            info = new EntityEffectInfo(this.version, entity, effects);
        else
            info = this.defaultInfo;

        this.entityInfoCache.put(entity.getId(), info);

        // Initialize the attached effects before returning.
        // Usually, the next step in processing would be
        // to tick the effects.
        info.activate(entity);

        return info;
    }

    @Override
    public Optional<EntityEffectInfo> findEntityEffectInfo(LivingEntity entity) {
        return Optional.ofNullable(this.entityInfoCache.get(entity.getId()));
    }

    @Override
    public Set<EntityEffectType> getEntityEffectTypes(EntityType<?> entityType) {
        return Collections.unmodifiableSet(this.entityEffects.computeIfAbsent(entityType, this::gatherEffectsFromConfigRules));
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
