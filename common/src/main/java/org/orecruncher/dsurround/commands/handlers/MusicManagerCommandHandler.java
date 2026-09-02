package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.client.sounds.MusicManager;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.music.DSurroundMusicManager;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;

public class MusicManagerCommandHandler {

    public static Component reset() {
        return execute(GameUtils.getMC().getMusicManager(), DSurroundMusicManager.Commands.RESET);
    }

    public static Component unpause() {
        return execute(GameUtils.getMC().getMusicManager(), DSurroundMusicManager.Commands.UNPAUSE);
    }

    public static Component pause() {
        return execute(GameUtils.getMC().getMusicManager(), DSurroundMusicManager.Commands.PAUSE);
    }

    public static Component whatsPlaying() {
        try {
            var mm = ReflectionHelper.cast(GameUtils.getMC().getMusicManager(), DSurroundMusicManager.class);
            if (mm.isPresent()) {
                var result =mm.get().whatsPlaying();
                return Component.translatable("dsurround.command.dsmm.whatsplaying.success", result);
            } else {
                return Component.translatable("dsurround.command.dsmm.notpresent");
            }
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm.whatsplaying.failure", t.getMessage());
        }
    }

    private static Component execute(MusicManager musicManager, DSurroundMusicManager.Commands command) {
        try {
            var mm = ReflectionHelper.cast(musicManager, DSurroundMusicManager.class);
            if (mm.isPresent()) {
                mm.get().doCommand(command);
                return Component.translatable("dsurround.command.dsmm." + command + ".success", command);
            } else {
                return Component.translatable("dsurround.command.dsmm.notpresent");
            }
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm." + command + ".failure", t.getMessage());
        }
    }
}
