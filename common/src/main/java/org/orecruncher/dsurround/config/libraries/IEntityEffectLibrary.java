package org.orecruncher.dsurround.config.libraries;

import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;

import java.util.Optional;
import java.util.Set;

/**
 * Which entity effects (breath, bow pull, ...) apply to entities, and their live state. Client thread only.
 */
public interface IEntityEffectLibrary extends ILibrary {
    boolean doesEntityEffectInfoExist(LivingEntity entity);

    /**
     * Deactivates and drops cached info for every entity not in {@code entitiesToRetain}.
     */
    void cleanCache(AbstractIntSet entitiesToRetain);

    /**
     * Deactivates and drops all cached entity info, for example when leaving a world (entity ids are only unique
     * within one).
     */
    void clearCache();

    /**
     * The info for the entity, creating and activating it if needed. For entities in effect range only.
     */
    EntityEffectInfo getEntityEffectInfo(LivingEntity entity);

    /**
     * The cached info for the entity, if any. Never creates or activates anything, so it is safe for diagnostics.
     */
    Optional<EntityEffectInfo> findEntityEffectInfo(LivingEntity entity);

    /**
     * The effect types configured for an entity type, whether or not they are currently enabled. Never creates or
     * activates anything.
     */
    Set<EntityEffectType> getEntityEffectTypes(EntityType<?> entityType);
}
