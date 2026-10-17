package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;

public final class Commands {

    public static void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        if (dispatcher == null)
            return;

        new AuroraCommand().register(dispatcher, registryAccess);
        new BiomeCommand().register(dispatcher, registryAccess);
        new ConflictsCommand().register(dispatcher, registryAccess);
        new DumpCommand().register(dispatcher, registryAccess);
        new ReloadCommand().register(dispatcher, registryAccess);
        new ScriptCommand().register(dispatcher, registryAccess);
        new MusicManagerCommand().register(dispatcher, registryAccess);
        new VersionCommand().register(dispatcher, registryAccess);
        new ProfileCommand().register(dispatcher, registryAccess);
        new WeatherCommand().register(dispatcher, registryAccess);
    }
}
