package org.orecruncher.dsurround.processing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.PotionUtil;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;

@Environment(EnvType.CLIENT)
public class PlayerHandler extends AbstractClientHandler {

    // This value will be stuffed into the data tracker.  Rendering logic looks for
    // color values > 0 to trigger rendering.
    private static final int HIDE_PARTICLE_SENTINEL = -1;

    public PlayerHandler(Configuration config, IModLog logger) {
        super("Player Handler", config, logger);
    }

    @Override
    public void process(final PlayerEntity player) {
        if (GameUtils.isInGame()) {
            int color = player.getDataTracker().get(LivingEntity.POTION_SWIRLS_COLOR);

            // If there is no color there are no particles to render
            if (color == 0)
                return;

            if (this.config.particleTweaks.suppressPlayerParticles) {
                final boolean hide = GameUtils.isFirstPersonView();
                if (hide) {
                    // If there is a color we need to suppress
                    if (color > 0)
                        suppressPotionParticles(player);
                } else {
                    // If we are to not hide the particles we need to turn them back on.
                    if (color < 0)
                        unsuppressPotionParticles(player);
                }
            } else if (color < 0) {
                // This is the case where if we suppressed particles but the user then turned off
                // suppression.
                unsuppressPotionParticles(player);
            }
        }
    }

    private static void suppressPotionParticles(PlayerEntity player) {
        player.getDataTracker().set(LivingEntity.POTION_SWIRLS_COLOR, HIDE_PARTICLE_SENTINEL);
    }

    private static void unsuppressPotionParticles(PlayerEntity player) {
        player.getDataTracker().set(LivingEntity.POTION_SWIRLS_COLOR, PotionUtil.getColor(player.getStatusEffects()));
    }
}
