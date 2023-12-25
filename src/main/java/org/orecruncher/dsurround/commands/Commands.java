package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class Commands {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (dispatcher == null)
            return;

        new BiomeCommand().register(dispatcher);
        new DumpCommand().register(dispatcher);
        new ReloadCommand().register(dispatcher);
        new ScriptCommand().register(dispatcher);
    }
}
