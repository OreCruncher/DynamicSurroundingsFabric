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
        ReloadCommand.register(ClientCommandManager.DISPATCHER);
    }

    public static void sendSuccess(final FabricClientCommandSource source, String command) {
        final String key = String.format("dsurround.command.%s.success", command);
        source.sendFeedback(new TranslatableText(key));
    }

    public static void sendSuccess(final FabricClientCommandSource source, final String command, String str1, String str2) {
        final String key = String.format("dsurround.command.%s.success", command);
        source.sendFeedback(new TranslatableText(key, str1, str2));
    }

    public static void sendFailure(final FabricClientCommandSource source, final String command) {
        final String key = String.format("dsurround.command.%s.failure", command);
        source.sendFeedback(new TranslatableText(key));
    }

    public static void sendFailure(final FabricClientCommandSource source, final String command, String str1) {
        final String key = String.format("dsurround.command.%s.failure", command);
        source.sendFeedback(new TranslatableText(key, str1));
    }
}
