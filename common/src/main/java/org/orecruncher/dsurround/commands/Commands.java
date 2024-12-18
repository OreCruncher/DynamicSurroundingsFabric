package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;

public final class Commands {

    public static void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        if (dispatcher == null)
            return;

        new BiomeCommand().register(dispatcher, registryAccess);
        new DumpCommand().register(dispatcher, registryAccess);
        new ReloadCommand().register(dispatcher, registryAccess);
        new ScriptCommand().register(dispatcher, registryAccess);
        new MusicManagerCommand().register(dispatcher, registryAccess);
    }
}
