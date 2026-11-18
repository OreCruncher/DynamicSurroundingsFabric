// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import org.orecruncher.dsurround.commands.handlers.DumpCommandHandler;

class DumpCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsdump";
    private static final String BIOMES = "biomes";
    private static final String SOUNDS = "sounds";
    private static final String DIMENSIONS = "dimensions";
    private static final String BLOCKS = "blocks";
    private static final String NOSTATES = "nostates";
    private static final String BLOCKSBYTAG = "blocksbytag";
    private static final String BLOCKCONFIGRULES = "blockconfigrules";
    private static final String BLOCKSTATES = "blockstates";
    private static final String ITEMS = "items";
    private static final String NAMESPACE = "namespace";
    private static final String TAGS = "tags";
    private static final String ENTITIES = "entities";
    private static final String DIREGISTRATIONS = "diregistrations";

    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND)
                .then(subCommand(BIOMES, DumpCommandHandler::dumpBiomes))
                .then(subCommand(SOUNDS, DumpCommandHandler::dumpSounds))
                .then(subCommand(DIMENSIONS, DumpCommandHandler::dumpDimensions))
                .then(subCommand(BLOCKS, () -> DumpCommandHandler.dumpBlocks(false))
                        .then(subCommand(NOSTATES, () -> DumpCommandHandler.dumpBlocks(true))))
                .then(subCommand(BLOCKSBYTAG, DumpCommandHandler::dumpBlocksByTag))
                .then(subCommand(BLOCKCONFIGRULES, DumpCommandHandler::dumpBlockConfigRules))
                .then(subCommand(BLOCKSTATES, DumpCommandHandler::dumpBlockState))
                .then(subCommand(ITEMS, () -> DumpCommandHandler.dumpItems(null))
                        .then(ClientCommandRegistrationEvent.argument(NAMESPACE, StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DumpCommandHandler.itemNamespaces(), builder))
                                .executes(ctx -> this.execute(ctx,
                                        () -> DumpCommandHandler.dumpItems(StringArgumentType.getString(ctx, NAMESPACE))))))
                .then(subCommand(TAGS, DumpCommandHandler::dumpTags))
                .then(subCommand(ENTITIES, DumpCommandHandler::dumpEntities))
                .then(subCommand(DIREGISTRATIONS, DumpCommandHandler::dumpDIRegistrations))
        );
    }
}
