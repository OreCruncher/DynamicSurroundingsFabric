package org.orecruncher.dsurround.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.Library;

import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

abstract class AbstractClientCommand {

    protected AbstractClientCommand() {

    }

    public abstract void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess);

    protected int execute(CommandContext<FabricClientCommandSource> ctx, Supplier<Component> commandHandler) {
        try {
            var result = commandHandler.get();
            ctx.getSource().sendFeedback(result);
            return 0;
        } catch(Exception ex) {
            Library.LOGGER.error(ex, "Unable to execute command %s", ctx.getCommand().toString());
            ctx.getSource().sendFeedback(Component.literal(ex.getMessage()));
            return 1;
        }
    }

    protected LiteralArgumentBuilder<FabricClientCommandSource> subCommand(String command, Supplier<Component> supplier) {
        return literal(command).executes(ctx -> this.execute(ctx, supplier));
    }
}
