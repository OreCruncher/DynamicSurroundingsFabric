package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.MusicManagerCommandHandler;
import org.orecruncher.dsurround.lib.music.DSurroundMusicManager;

public class MusicManagerCommand extends AbstractClientCommand {
    private static final String COMMAND = "dsmm";
    private static final String RESET = DSurroundMusicManager.Commands.RESET.commandName();
    private static final String PAUSE = DSurroundMusicManager.Commands.PAUSE.commandName();
    private static final String UNPAUSE = DSurroundMusicManager.Commands.UNPAUSE.commandName();
    private static final String WHATS_PLAYING = "whatsplaying";

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND)
                .then(subCommand(RESET, MusicManagerCommandHandler::reset))
                .then(subCommand(PAUSE, MusicManagerCommandHandler::pause))
                .then(subCommand(UNPAUSE, MusicManagerCommandHandler::unpause))
                .then(subCommand(WHATS_PLAYING, MusicManagerCommandHandler::whatsPlaying)));
    }
}
