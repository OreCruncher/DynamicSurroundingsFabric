package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.BlockLibrary;
import org.orecruncher.dsurround.config.DimensionLibrary;
import org.orecruncher.dsurround.config.SoundLibrary;

@Environment(EnvType.CLIENT)
public class ReloadCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("dsreload").executes(ReloadCommand::execute));
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        try {
            SoundLibrary.load();
            BlockLibrary.load();
            BiomeLibrary.load();
            DimensionLibrary.load();
            Commands.sendSuccess(ctx.getSource(), "reload");
        } catch(Throwable t) {
            Client.LOGGER.error(t, "Configuration reload failed");
            Commands.sendFailure(ctx.getSource(), "reload");
            return 1;
        }
        return 0;
    }
}