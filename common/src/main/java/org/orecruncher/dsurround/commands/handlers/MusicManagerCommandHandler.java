package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.mixinutils.IMusicManager;

public class MusicManagerCommandHandler {

    public static Component reset() {
        try {
            ((IMusicManager)(GameUtils.getMC().getMusicManager())).dsurround_doCommand("reset");
            return Component.translatable("dsurround.command.dsmm.reset.success");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm.reset.failure", t.getMessage());
        }
    }

    public static Component unpause() {
        try {
            ((IMusicManager)(GameUtils.getMC().getMusicManager())).dsurround_doCommand("unpause");
            return Component.translatable("dsurround.command.dsmm.unpause.success");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm.unpause.failure", t.getMessage());
        }
    }

    public static Component pause() {
        try {
            ((IMusicManager)(GameUtils.getMC().getMusicManager())).dsurround_doCommand("pause");
            return Component.translatable("dsurround.command.dsmm.pause.success");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm.pause.failure", t.getMessage());
        }
    }
}
