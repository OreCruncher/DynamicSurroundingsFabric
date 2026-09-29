package org.orecruncher.dsurround.effects.entity;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.CachingSupplier;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Collection;

public class EntityEffectInfo {

    private final int entityId;
    private final int version;
    private final CachingSupplier<LivingEntity> entityReference;
    private final Collection<IEntityEffect> effects;

    /**
     * Special constructor for creating a default instance
     */
    private EntityEffectInfo(int version) {
        this(version, null, ImmutableList.of());
    }

    public EntityEffectInfo(int version, LivingEntity entity, Collection<IEntityEffect> effects) {
        this.version = version;
        this.entityId = entity != null ? entity.getId() : -1;
        this.effects = effects;

        if (this.entityId == -1) {
            this.entityReference = CachingSupplier.from(() -> { throw new RuntimeException(); });
        } else {
            this.entityReference = CachingSupplier.from(() ->
                    GameUtils.getWorld()
                    .map(l -> l.getEntity(this.entityId))
                    .map(LivingEntity.class::cast)
                    .orElseThrow());
        }
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

    @NotNull
    public LivingEntity getEntity() {
        return this.entityReference.get();
    }

    public void activate() {
        // If the entity is already removed, do nothing.
        if (this.entityReference.get().isAlive())
            for (var e : this.effects)
                e.activate(this);
        this.entityReference.clear();
    }

    public void deactivate() {
        // Need to deactivate regardless of whether the entity has been removed. There may be
        // resources that need to be cleaned up.
        for (var e : this.effects)
            e.deactivate(this);
        this.entityReference.clear();
    }

    public void tick() {
        // Do not tick if already removed
        if (this.entityReference.get().isAlive())
            for (var e : this.effects)
                e.tick(this);
        this.entityReference.clear();
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
            public void activate() {}
            @Override
            public void deactivate() {}
            @Override
            public void tick() {}
            @Override
            public boolean isCurrentPlayer(LivingEntity player) {
                return false;
            }
        };
    }
}
