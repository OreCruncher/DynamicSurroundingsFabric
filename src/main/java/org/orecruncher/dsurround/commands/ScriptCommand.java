package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.MessageArgumentType;
import org.orecruncher.dsurround.lib.commands.client.ClientCommand;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class ScriptCommand extends ClientCommand {

    ScriptCommand() {
        super("dsscript");
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(this.command)
                .then(argument("script", MessageArgumentType.message()).executes(this::execute)));
    }

    private int execute(CommandContext<FabricClientCommandSource> ctx) {
        var script = ctx.getArgument("script", MessageArgumentType.MessageFormat.class);
        var handlerResult = ScriptCommandHandler.execute(script.getContents());
        ctx.getSource().sendFeedback(handlerResult);
        return 0;
    }
}
