package org.orecruncher.dsurround.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

public final class Commands {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        if (dispatcher == null)
            return;

        new BiomeCommand().register(dispatcher, registryAccess);
        new DumpCommand().register(dispatcher, registryAccess);
        new ReloadCommand().register(dispatcher, registryAccess);
        new ScriptCommand().register(dispatcher, registryAccess);
    }
}
