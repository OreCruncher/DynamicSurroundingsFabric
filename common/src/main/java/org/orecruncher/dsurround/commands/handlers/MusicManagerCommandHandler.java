package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.client.sounds.MusicManager;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixinutils.IMusicManager;

import java.util.Optional;

public class MusicManagerCommandHandler {

    public static Component reset() {
        return execute(GameUtils.getMC().getMusicManager(), "reset");
    }

    public static Component unpause() {
        return execute(GameUtils.getMC().getMusicManager(), "unpause");
    }

    public static Component pause() {
        return execute(GameUtils.getMC().getMusicManager(), "pause");
    }

    public static Component whatsPlaying() {
        try {
            Optional<IMusicManager> mm = ReflectionHelper.cast(GameUtils.getMC().getMusicManager());
            if (mm.isPresent()) {
                var result =mm.get().dsurround_whatsPlaying();
                return Component.translatable("dsurround.command.dsmm.whatsplaying.success", result);
            } else {
                return Component.translatable("dsurround.command.dsmm.notpresent");
            }
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm.whatsplaying.failure", t.getMessage());
        }
    }

    private static Component execute(MusicManager musicManager, String command) {
        try {
            Optional<IMusicManager> mm = ReflectionHelper.cast(musicManager);
            if (mm.isPresent()) {
                mm.get().dsurround_doCommand(command);
                return Component.translatable("dsurround.command.dsmm." + command + ".success", command);
            } else {
                return Component.translatable("dsurround.command.dsmm.notpresent");
            }
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsmm." + command + ".failure", t.getMessage());
        }
    }
}
