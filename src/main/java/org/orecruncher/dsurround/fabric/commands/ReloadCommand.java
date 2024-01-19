package org.orecruncher.dsurround.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.ReloadCommandHandler;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class ReloadCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsreload";

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal(COMMAND).executes(this::execute));
    }

    private int execute(CommandContext<FabricClientCommandSource> ctx) {
        return this.execute(ctx, ReloadCommandHandler::execute);
    }
}