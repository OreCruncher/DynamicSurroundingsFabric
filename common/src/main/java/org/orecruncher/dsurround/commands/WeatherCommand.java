package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.WeatherCommandHandler;

/**
 * /dsweather shows the storm's strength; /dsweather strength 0-100 sets it, as a percent of full strength, until
 * /dsweather clear
 */
class WeatherCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsweather";
    private static final String STRENGTH = "strength";
    private static final String CLEAR = "clear";
    private static final String PERCENT_PARAMETER = "percent";

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND)
                .executes(ctx -> this.execute(ctx, WeatherCommandHandler::status))
                .then(ClientCommandRegistrationEvent.literal(STRENGTH)
                        .then(ClientCommandRegistrationEvent.argument(PERCENT_PARAMETER, IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> this.execute(ctx,
                                        () -> WeatherCommandHandler.strength(IntegerArgumentType.getInteger(ctx, PERCENT_PARAMETER))))))
                .then(subCommand(CLEAR, WeatherCommandHandler::clear)));
    }
}
