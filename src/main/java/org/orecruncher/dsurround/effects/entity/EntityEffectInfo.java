package org.orecruncher.dsurround.effects.entity;

import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.lib.GameUtils;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Optional;

public class EntityEffectInfo {

    // Entity can go out of scope for a variety of reasons besides
    // death.  Maintain weak reference to allow it to go out of scope.
    private final WeakReference<LivingEntity> entity;
    private final Collection<IEntityEffect> effects;
    private final int version;

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

    public Optional<LivingEntity> getEntity() {
        return Optional.ofNullable(this.entity.get());
    }

    public void activate() {
        for (var e : effects)
            e.activate(this);
    }

    public void deactivate() {
        for (var e : effects)
            e.deactivate(this);
    }

    public void tick() {
        for (var e : effects)
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
     * Checks whether the entity is still loaded, alive, and within effect range of the player.
     */
    public boolean isAlive() {
        return this.getEntity().map(LivingEntity::isAlive).orElse(false);
    }
}
