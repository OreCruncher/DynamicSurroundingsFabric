package org.orecruncher.dsurround.processing;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.logging.IModLog;

public class EntityEffectHandler  extends AbstractClientHandler {

    private final IEntityEffectLibrary entityEffectLibrary;

    public EntityEffectHandler(Configuration config, IEntityEffectLibrary entityEffectLibrary, IModLog logger) {
        super("EntityEffect Handler", config, logger);

        this.entityEffectLibrary = entityEffectLibrary;
    }

    @Override
    public void process(final Player player) {

        var range = this.config.entityEffects.entityEffectRange;
        var world = player.level();

        // Get living entities in the world.  Since the API does some fancy tracking of entities we create a box
        // larger than the normal range size.
        var worldBox = AABB.unitCubeFromLowerCorner(player.getEyePosition()).inflate(range * 2);
        var loadedEntities = world.getEntitiesOfClass(LivingEntity.class, worldBox);

        for (var entity : loadedEntities) {
            var hasInfo = this.entityEffectLibrary.doesEntityEffectInfoExist(entity);
            var inRange = entity.closerThan(player, range);
            EntityEffectInfo info = null;

            if (!hasInfo && entity.isAlive()) {
                // If it does not have info, but is alive, and is not a spectator get info for it.
                if (inRange) {
                    info = this.entityEffectLibrary.getEntityEffectInfo(entity);
                }
            } else if (hasInfo) {
                // If it does have info, get whatever is currently cached
                info = this.entityEffectLibrary.getEntityEffectInfo(entity);
            }

            if (info != null) {
                if (inRange && info.isAlive() && !entity.isSpectator()) {
                    info.tick();
                } else {
                    info.deactivate();
                    this.entityEffectLibrary.clearEntityEffectInfo(entity);
                }
            }
        }
    }
}
