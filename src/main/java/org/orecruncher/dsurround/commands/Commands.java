package org.orecruncher.dsurround.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class Commands {

    public static void register() {
        BiomeCommand.register(ClientCommandManager.getActiveDispatcher());
        ScriptCommand.register(ClientCommandManager.getActiveDispatcher());
        DumpCommand.register(ClientCommandManager.getActiveDispatcher());
        ReloadCommand.register(ClientCommandManager.getActiveDispatcher());
        TimeOfDayCommand.register(ClientCommandManager.getActiveDispatcher());
    }

    public static void sendSuccess(final FabricClientCommandSource source, String command) {
        final String key = String.format("dsurround.command.%s.success", command);
        source.sendFeedback(Text.translatable(key));
    }

    public static void sendSuccess(final FabricClientCommandSource source, final String command, String str1, String str2) {
        final String key = String.format("dsurround.command.%s.success", command);
        source.sendFeedback(Text.translatable(key, str1, str2));
    }

    public static void sendFailure(final FabricClientCommandSource source, final String command) {
        final String key = String.format("dsurround.command.%s.failure", command);
        source.sendFeedback(Text.translatable(key));
    }

    public static void sendFailure(final FabricClientCommandSource source, final String command, String str1) {
        final String key = String.format("dsurround.command.%s.failure", command);
        source.sendFeedback(Text.translatable(key, str1));
    }
}
