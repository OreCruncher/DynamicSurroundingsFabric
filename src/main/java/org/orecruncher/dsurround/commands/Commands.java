package org.orecruncher.dsurround.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;

@Environment(EnvType.CLIENT)
public final class Commands {

    public static void register() {
        BiomeCommand.register(ClientCommandManager.DISPATCHER);
        ScriptCommand.register(ClientCommandManager.DISPATCHER);
    }
}
