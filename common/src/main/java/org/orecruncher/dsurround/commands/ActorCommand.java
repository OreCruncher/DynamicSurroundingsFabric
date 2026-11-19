// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.ActorCommandHandler;
import org.orecruncher.dsurround.config.ActorKind;

/**
 * /dsactor lists the world actors about; /dsactor dust_devil starts one ahead of the player, whatever the biome and
 * weather, for testing. /dsactor dust_devil 42 starts the one seed 42 makes (its size, course and life), so a bench
 * scene summons the same ones every run; without a seed one is picked, and the reply names it.
 */
class ActorCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsactor";
    private static final String SEED = "seed";

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        var command = ClientCommandRegistrationEvent.literal(COMMAND)
                .executes(ctx -> this.execute(ctx, ActorCommandHandler::status));
        for (var kind : ActorKind.values())
            command.then(subCommand(kind.getName(), () -> ActorCommandHandler.summon(kind, null))
                    .then(ClientCommandRegistrationEvent.argument(SEED, LongArgumentType.longArg())
                            .executes(ctx -> this.execute(ctx,
                                    () -> ActorCommandHandler.summon(kind, LongArgumentType.getLong(ctx, SEED))))));
        dispatcher.register(command);
    }
}
