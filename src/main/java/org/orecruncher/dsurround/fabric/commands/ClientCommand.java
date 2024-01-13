package org.orecruncher.dsurround.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.Library;

import java.util.function.Supplier;

abstract class ClientCommand {

    protected final String command;

    protected ClientCommand(String command) {
        this.command = command;
    }

    public abstract void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess);

    protected int execute(CommandContext<FabricClientCommandSource> ctx, Supplier<Component> commandHandler) {
        try {
            var result = commandHandler.get();
            ctx.getSource().sendFeedback(result);
            return 0;
        } catch(Exception ex) {
            Library.getLogger().error(ex, "Unable to execute command %s", ctx.getCommand().toString());
            ctx.getSource().sendFeedback(Component.literal(ex.getMessage()));
            return 1;
        }
    }
}
