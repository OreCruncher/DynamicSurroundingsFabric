package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.Library;

import java.util.function.Supplier;

abstract class AbstractClientCommand {

    protected AbstractClientCommand() {

    }

    public abstract void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess);

    protected int execute(CommandContext<ClientCommandRegistrationEvent.ClientCommandSourceStack> ctx, Supplier<Component> commandHandler) {
        try {
            var result = commandHandler.get();
            ctx.getSource().arch$getPlayer().displayClientMessage(result, false);
            return 0;
        } catch(Exception ex) {
            Library.LOGGER.error(ex, "Unable to execute command %s", ctx.getCommand().toString());
            ctx.getSource().arch$getPlayer().displayClientMessage(Component.literal(ex.getMessage()), false);
            return 1;
        }
    }

    protected LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> subCommand(String command, Supplier<Component> supplier) {
        return ClientCommandRegistrationEvent.literal(command).executes(ctx -> this.execute(ctx, supplier));
    }
}
