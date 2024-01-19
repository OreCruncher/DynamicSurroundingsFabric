package org.orecruncher.dsurround.processing;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionUtils;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.mixinutils.ILivingEntityExtended;

public class PotionParticleSuppressionHandler extends AbstractClientHandler {

    // This value will be stuffed into the data tracker.  Rendering logic looks for
    // color values > 0 to trigger rendering.
    private static final int HIDE_PARTICLE_SENTINEL = -1;

    public PotionParticleSuppressionHandler(Configuration config, IModLog logger) {
        super("Player Handler", config, logger);
    }

    @Override
    public void process(final Player player) {
        if (GameUtils.isInGame()) {
            int color = getPotionParticleColor(player);

            // If there is no color, there are no particles to render
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

    private static int getPotionParticleColor(Player player) {
        var accessor = (ILivingEntityExtended)player;
        return accessor.dsurround_getPotionSwirlColor();
    }

    private static void suppressPotionParticles(Player player) {
        var accessor = (ILivingEntityExtended)player;
        accessor.dsurround_setPotionSwirlColor(HIDE_PARTICLE_SENTINEL);
    }

    private static void unsuppressPotionParticles(Player player) {
        var accessor = (ILivingEntityExtended)player;
        accessor.dsurround_setPotionSwirlColor(PotionUtils.getColor(player.getActiveEffects()));
    }
}
