package org.orecruncher.dsurround.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.TranslatableText;

@Environment(EnvType.CLIENT)
public final class Commands {

    public static void register() {
        BiomeCommand.register(ClientCommandManager.DISPATCHER);
        ScriptCommand.register(ClientCommandManager.DISPATCHER);
        DumpCommand.register(ClientCommandManager.DISPATCHER);
    }

    public static void sendSuccess(final FabricClientCommandSource source, final String command, String operation, String target) {
        final String key = String.format("dsurround.command.%s.success", command);
        source.sendFeedback(new TranslatableText(key, operation, target));
    }

    public static void sendFailure(final FabricClientCommandSource source, final String command) {
        final String key = String.format("dsurround.command.%s.failure", command);
        source.sendFeedback(new TranslatableText(key));
    }
}
