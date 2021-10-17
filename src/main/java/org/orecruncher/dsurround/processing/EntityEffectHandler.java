package org.orecruncher.dsurround.processing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.EntityEffectLibrary;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class EntityEffectHandler  extends ClientHandler {

    private static final IModLog LOGGER = Client.LOGGER.createChild(EntityEffectHandler.class);

    EntityEffectHandler() {
        super("EntityEffect Handler");
    }

    @Override
    public void process(final PlayerEntity player) {

        var range = Client.Config.entityEffects.entityEffectRange;
        var world = player.getEntityWorld();

        // Get living entities in the world.  Since the API does some fancy tracking of entities we create a box
        // larger than the normal range size.
        var worldBox = Box.from(player.getEyePos()).expand(range * 2);
        var loadedEntities = world.getEntitiesByClass(LivingEntity.class, worldBox, entity -> true);

        for (var entity : loadedEntities) {
            var hasInfo = EntityEffectLibrary.doesEntityEffectInfoExist(entity);
            var inRange = entity.isInRange(player, range);
            EntityEffectInfo info = null;

            if (!hasInfo && entity.isAlive()) {
                // If it does not have info, but is alive, and is not a spectator get info for it.
                if (inRange && !entity.isSpectator()) {
                    LOGGER.debug("Obtaining effect info for %s (id %d)", entity.getClass().getSimpleName(), entity.getId());
                    info = EntityEffectLibrary.getEntityEffectInfo(entity);
                    EntityEffectInfo finalInfo = info;
                    LOGGER.debug(() -> {
                        var txt = finalInfo.getEffects().stream()
                                .map(e -> e.getClass().getSimpleName())
                                .collect(Collectors.joining(","));
                        return String.format("Effects attached: %s", txt);
                    });
                }
            } else if (hasInfo) {
                // If it does have info just get whatever is currently cached
                info = EntityEffectLibrary.getEntityEffectInfo(entity);
            }

            if (info != null) {
                if (inRange && info.isAlive() && !entity.isSpectator()) {
                    info.tick();
                } else {
                    LOGGER.debug("Clearing effect info for %s (id %d)", entity.getClass().getSimpleName(), entity.getId());
                    info.deactivate();
                    EntityEffectLibrary.clearEntityEffectInfo(entity);
                }
            }
        }
    }
}
