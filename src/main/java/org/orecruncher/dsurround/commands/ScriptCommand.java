package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

@Environment(EnvType.CLIENT)
final class ScriptCommand {

    public static void register(@Nullable CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.register(
                ClientCommandManager.literal("dsscript")
                        .then(argument("script", MessageArgumentType.message())
                                .executes(ScriptCommand::execute)));
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        var script = ctx.getArgument("script", MessageArgumentType.MessageFormat.class);
        var result = ContainerManager.resolve(IConditionEvaluator.class).eval(new Script(script.getContents()));
        ctx.getSource().sendFeedback(Text.of(result.toString()));
        return 0;
    }

}
