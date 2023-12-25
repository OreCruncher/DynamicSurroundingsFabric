package org.orecruncher.dsurround.platform.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.MessageArgumentType;
import org.orecruncher.dsurround.commands.ScriptCommandHandler;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class ScriptCommand extends ClientCommand {

    private static final String SCRIPT_PARAMETER = "script";

    ScriptCommand() {
        super("dsscript");
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(this.command)
                .then(argument(SCRIPT_PARAMETER, MessageArgumentType.message()).executes(this::execute)));
    }

    private int execute(CommandContext<FabricClientCommandSource> ctx) {
        var script = ctx.getArgument(SCRIPT_PARAMETER, MessageArgumentType.MessageFormat.class);
        return this.execute(ctx, () -> ScriptCommandHandler.execute(script.getContents()));
    }
}
