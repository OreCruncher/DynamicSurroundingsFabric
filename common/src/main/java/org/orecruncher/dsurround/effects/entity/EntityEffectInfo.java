package org.orecruncher.dsurround.effects.entity;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Collection;

/**
 * The effects attached to one entity. Effect instances are shared between entities, so anything specific to the
 * entity is reached through this object.
 * <p>
 * No reference to the entity is kept between calls, so the cache holding these never keeps an entity alive. During
 * {@link #activate} and {@link #tick} the entity is supplied by the caller (who already has it), so effects asking
 * for {@link #getEntity()} get it without a lookup. Outside those calls it is looked up by id.
 */
public class EntityEffectInfo {

    private final int entityId;
    private final int version;
    private final Collection<IEntityEffect> effects;

    // The entity being activated or ticked; only set during those calls
    private @Nullable LivingEntity current;

    /**
     * Special constructor for creating a default instance
     */
    private EntityEffectInfo(int version) {
        this(version, null, ImmutableList.of());
    }

    public EntityEffectInfo(int version, @Nullable LivingEntity entity, Collection<IEntityEffect> effects) {
        this.version = version;
        this.entityId = entity != null ? entity.getId() : -1;
        this.effects = effects;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isDefault() {
        return false;
    }

    public int getEntityId() {
        return this.entityId;
    }

    /**
     * The entity these effects belong to. Throws if it can't be found (no world, or no living entity with this id).
     */
    @NotNull
    public LivingEntity getEntity() {
        var entity = this.current;
        if (entity != null)
            return entity;
        return GameUtils.getWorld()
                .map(l -> l.getEntity(this.entityId))
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .orElseThrow();
    }

    /**
     * Initializes the effects for {@code entity}, the entity this info was created for.
     */
    public void activate(LivingEntity entity) {
        // If the entity is already removed, do nothing.
        if (!entity.isAlive())
            return;
        this.current = entity;
        try {
            for (var e : this.effects)
                e.activate(this);
        } finally {
            this.current = null;
        }
    }

    public void deactivate() {
        // Need to deactivate regardless of whether the entity has been removed. There may be
        // resources that need to be cleaned up.
        for (var e : this.effects)
            e.deactivate(this);
    }

    /**
     * Ticks the effects for {@code entity}, the entity this info was created for.
     */
    public void tick(LivingEntity entity) {
        // Do not tick if already removed
        if (!entity.isAlive())
            return;
        this.current = entity;
        try {
            for (var e : this.effects)
                e.tick(this);
        } finally {
            this.current = null;
        }
    }

    // Use only for diagnostic purposes
    public Collection<IEntityEffect> getEffects() {
        return this.effects;
    }

    public boolean isCurrentPlayer(LivingEntity player) {
        return GameUtils.getPlayer().map(p -> p.getId() == player.getId()).orElse(false);
    }

    /**
     * Creates a default instance of the EntityEffectInfo class
     */
    public static EntityEffectInfo createDefault(int version) {
        return new EntityEffectInfo(version) {
            @Override
            public boolean isDefault() {
                return true;
            }
            @Override
            public @NotNull LivingEntity getEntity() {
                throw new RuntimeException("No entity associated with default entity effect info");
            }
            @Override
            public void activate(LivingEntity entity) {}
            @Override
            public void deactivate() {}
            @Override
            public void tick(LivingEntity entity) {}
            @Override
            public boolean isCurrentPlayer(LivingEntity player) {
                return false;
            }
        };
    }
}
