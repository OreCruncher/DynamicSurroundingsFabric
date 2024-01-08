package org.orecruncher.dsurround.effects.entity;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.GameUtils;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Objects;

public class EntityEffectInfo {

    // Entity can go out of scope for a variety of reasons besides
    // death.  Maintain weak reference to allow it to go out of scope.
    private final WeakReference<LivingEntity> entity;
    private final Collection<IEntityEffect> effects;
    private final int version;

    /**
     * Special constructor for creating a default instance
     */
    private EntityEffectInfo(int version) {
        this(version, null, ImmutableList.of());
    }

    public EntityEffectInfo(int version, LivingEntity entity, Collection<IEntityEffect> effects) {
        this.version = version;
        this.entity = new WeakReference<>(entity);
        this.effects = effects;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isDefault() {
        return false;
    }

    @NotNull
    public LivingEntity getEntity() {
        return Objects.requireNonNull(this.entity.get());
    }

    public void activate() {
        // If the entity is already removed, do nothing.
        if (this.isRemoved())
            return;
        for (var e : this.effects)
            e.activate(this);
    }

    public void deactivate() {
        // Need to deactivate regardless of whether the entity has been removed. There may be
        // resources that need to be cleaned up.
        for (var e : this.effects)
            e.deactivate(this);
    }

    public void tick() {
        // Do not tick if already removed
        if (this.isRemoved())
            return;
        for (var e : this.effects)
            e.tick(this);
    }

    // Use only for diagnostic purposes
    public Collection<IEntityEffect> getEffects() {
        return this.effects;
    }

    public boolean isCurrentPlayer(LivingEntity player) {
        return GameUtils.getPlayer().map(p -> p.getId() == player.getId()).orElse(false);
    }

    /**
     * Checks whether the entity has been unloaded, or is not alive
     */
    public boolean isRemoved() {
        var e = this.entity.get();
        return e == null || e.isRemoved();
    }

    /**
     * Checks whether the entity is still loaded and alive
     */
    public boolean isAlive() {
        return !this.isRemoved();
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
            @Override
            public boolean isRemoved() {
                return false;
            }
            @Override
            public boolean isAlive()
            {
                return true;
            }
        };
    }
}
