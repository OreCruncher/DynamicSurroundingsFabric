package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.MusicManagerCommandHandler;

public class MusicManagerCommand extends AbstractClientCommand {
    private static final String COMMAND = "dsmm";
    private static final String RESET = "reset";

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND)
                .then(subCommand(RESET, MusicManagerCommandHandler::reset)));
    }
}
