package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.ReloadCommandHandler;

class ReloadCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsreload";

    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND).executes(this::execute));
    }

    private int execute(CommandContext<ClientCommandRegistrationEvent.ClientCommandSourceStack> ctx) {
        return this.execute(ctx, ReloadCommandHandler::execute);
    }
}