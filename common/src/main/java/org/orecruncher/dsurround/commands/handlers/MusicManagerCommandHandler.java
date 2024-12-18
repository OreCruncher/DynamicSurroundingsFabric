package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.mixinutils.IMusicManager;

public class MusicManagerCommandHandler {

    public static Component reset() {
        try {
            ((IMusicManager)(GameUtils.getMC().getMusicManager())).dsurround_reset();
            return Component.translatable("dsurround.command.dsmm.reset.success");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm.reset.failure", t.getMessage());
        }
    }
}
