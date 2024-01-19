package org.orecruncher.dsurround.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.commands.DumpCommandHandler;

import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

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
    private static final String TAGS = "tags";
    private static final String DIREGISTRATIONS = "diregistrations";

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal(COMMAND)
                .then(subCommand(BIOMES, DumpCommandHandler::dumpBiomes))
                .then(subCommand(SOUNDS, DumpCommandHandler::dumpSounds))
                .then(subCommand(DIMENSIONS, DumpCommandHandler::dumpDimensions))
                .then(subCommand(BLOCKS, () -> DumpCommandHandler.dumpBlocks(false))
                        .then(subCommand(NOSTATES, () -> DumpCommandHandler.dumpBlocks(false))))
                .then(subCommand(BLOCKSBYTAG, DumpCommandHandler::dumpBlocksByTag))
                .then(subCommand(BLOCKCONFIGRULES, DumpCommandHandler::dumpBlockConfigRules))
                .then(subCommand(BLOCKSTATES, DumpCommandHandler::dumpBlockState))
                .then(subCommand(ITEMS, DumpCommandHandler::dumpItems))
                .then(subCommand(TAGS, DumpCommandHandler::dumpTags))
                .then(subCommand(DIREGISTRATIONS, DumpCommandHandler::dumpDIRegistrations))
        );
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> subCommand(String command, Supplier<Component> supplier) {
        return literal(command).executes(ctx -> this.execute(ctx, supplier));
    }
}
