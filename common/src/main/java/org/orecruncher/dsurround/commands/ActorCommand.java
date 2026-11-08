// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.ActorCommandHandler;
import org.orecruncher.dsurround.config.ActorKind;

/**
 * /dsactor lists the world actors about; /dsactor dust_devil starts one ahead of the player, whatever the biome and
 * weather, for testing.
 */
class ActorCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsactor";

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        var command = ClientCommandRegistrationEvent.literal(COMMAND)
                .executes(ctx -> this.execute(ctx, ActorCommandHandler::status));
        for (var kind : ActorKind.values())
            command.then(subCommand(kind.getName(), () -> ActorCommandHandler.summon(kind)));
        dispatcher.register(command);
    }
}
