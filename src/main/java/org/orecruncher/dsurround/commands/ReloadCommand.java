package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.impl.*;

@Environment(EnvType.CLIENT)
public class ReloadCommand {
    public static void register(@Nullable CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.register(
                ClientCommandManager.literal("dsreload").executes(ReloadCommand::execute));
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        try {
            AssetLibraryEvent.reload();
            Commands.sendSuccess(ctx.getSource(), "reload");
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Configuration reload failed");
            Commands.sendFailure(ctx.getSource(), "reload");
            return 1;
        }
        return 0;
    }
}