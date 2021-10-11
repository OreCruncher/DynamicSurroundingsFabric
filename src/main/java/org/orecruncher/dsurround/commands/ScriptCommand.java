package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.text.LiteralText;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.argument;

@Environment(EnvType.CLIENT)
final class ScriptCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("dsscript")
                        .then(argument("script", MessageArgumentType.message())
                                .executes(ScriptCommand::execute)));
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        var script = ctx.getArgument("script", MessageArgumentType.MessageFormat.class);
        var result = ConditionEvaluator.INSTANCE.eval(new Script(script.getContents()));
        ctx.getSource().sendFeedback(new LiteralText(result.toString()));
        return 0;
    }

}
