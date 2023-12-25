package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.orecruncher.dsurround.lib.commands.client.ClientCommand;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class ReloadCommand extends ClientCommand {

    ReloadCommand() {
        super("dsreload");
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(this.command).executes(this::execute));
    }

    private int execute(CommandContext<FabricClientCommandSource> ctx) {
        var handlerResult = ReloadCommandHandler.execute();
        ctx.getSource().sendFeedback(handlerResult);
        return 0;
    }
}