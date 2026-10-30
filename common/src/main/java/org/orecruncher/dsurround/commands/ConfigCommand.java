package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.commands.handlers.ConfigCommandHandler;
import org.orecruncher.dsurround.lib.config.ConfigPreset;
import org.orecruncher.dsurround.lib.di.ContainerManager;

/**
 * /dsconfig preset lists the config presets, and /dsconfig preset name applies one, as the config screen's buttons do.
 */
class ConfigCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsconfig";
    private static final String PRESET = "preset";
    private static final String NAME_PARAMETER = "name";

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND)
                .then(ClientCommandRegistrationEvent.literal(PRESET)
                        .executes(ctx -> this.execute(ctx, () -> ConfigCommandHandler.presets(config())))
                        .then(ClientCommandRegistrationEvent.argument(NAME_PARAMETER, StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        config().getPresets().stream().map(ConfigPreset::id), builder))
                                .executes(ctx -> this.execute(ctx,
                                        () -> ConfigCommandHandler.preset(config(), StringArgumentType.getString(ctx, NAME_PARAMETER)))))));
    }

    private static Configuration config() {
        return ContainerManager.resolve(Configuration.class);
    }
}
