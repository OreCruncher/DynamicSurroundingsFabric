package org.orecruncher.dsurround.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.MessageArgument;
import org.orecruncher.dsurround.commands.ScriptCommandHandler;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class ScriptCommand extends ClientCommand {

    private static final String SCRIPT_PARAMETER = "script";

    ScriptCommand() {
        super("dsscript");
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal(this.command)
                .then(argument(SCRIPT_PARAMETER, MessageArgument.message()).executes(this::execute)));
    }

    private int execute(CommandContext<FabricClientCommandSource> ctx) {
        var script = ctx.getArgument(SCRIPT_PARAMETER, MessageArgument.Message.class);
        return this.execute(ctx, () -> ScriptCommandHandler.execute(script.getText()));
    }
}
