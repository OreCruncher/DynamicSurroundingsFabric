package org.orecruncher.dsurround.processing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.reflection.ReflectedMethod;

@Environment(EnvType.CLIENT)
public class PlayerHandler extends ClientHandler {

    private static final ReflectedMethod<Void> clearPotionSwirls = new ReflectedMethod<>(LivingEntity.class, "clearPotionSwirls", "method_6069");

    PlayerHandler() {
        super("Player Handler");
    }

    @Override
    public void process(final PlayerEntity player) {
        if (Client.Config.particleTweaks.suppressPlayerParticles && GameUtils.isInGame()) {
            if (clearPotionSwirls.isNotAvailable()) {
                Client.LOGGER.info("clearPotionSwirls not available");
                return;
            }
            final boolean hide = GameUtils.isFirstPersonView();
            if (hide) {
                clearPotionSwirls.invoke(GameUtils.getPlayer());
            }
        }
    }
}
