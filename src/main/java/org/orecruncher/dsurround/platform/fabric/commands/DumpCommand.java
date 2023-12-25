package org.orecruncher.dsurround.platform.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.orecruncher.dsurround.commands.DumpCommandHandler;
import org.orecruncher.dsurround.lib.commands.client.ClientCommand;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class DumpCommand extends ClientCommand {

    DumpCommand() {
        super("dsdump");
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(this.command)
                .then(
                        literal("biomes")
                                .executes(cmd -> this.execute(cmd, DumpCommandHandler::dumpBiomes)))
                .then(
                        literal("sounds")
                                .executes(cmd -> this.execute(cmd, DumpCommandHandler::dumpSounds)))
                .then(
                        literal("dimensions")
                                .executes(cmd -> this.execute(cmd, DumpCommandHandler::dumpDimensions)))
                .then(
                        literal("blocks")
                                .executes(cmd -> this.execute(cmd, () -> DumpCommandHandler.dumpBlocks(false)))
                            .then(literal("nostates")
                                .executes(cmd -> this.execute(cmd, () -> DumpCommandHandler.dumpBlocks(true)))))
                .then(
                        literal("blocksbytag")
                                .executes(cmd -> this.execute(cmd, DumpCommandHandler::dumpBlocksByTag)))
                .then(
                        literal("blockconfigrules")
                                .executes(cmd -> this.execute(cmd, DumpCommandHandler::dumpBlockConfigRules)))
                .then(
                        literal("blockstates")
                                .executes(cmd -> this.execute(cmd, DumpCommandHandler::dumpBlockState)))
                .then(
                        literal("items")
                                .executes(cmd -> this.execute(cmd, DumpCommandHandler::dumpItems)))
        );
    }
}
