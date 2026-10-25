package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.WeatherCommandHandler;
import org.orecruncher.dsurround.effects.lightning.Strike;
import org.orecruncher.dsurround.runtime.weather.Wind;

import java.util.Locale;

/**
 * /dsweather shows the storm's strength; /dsweather strength 0-100 sets it, as a percent of full strength, until
 * /dsweather clear. /dsweather wind does the same for the wind; groundfog and frost set the ground fog's thickness and
 * the frost on windows, and clouds the clouds' coverage and thickness. /dsweather lightning strikes ahead of the player: a bolt, or spider lightning or a flash in the
 * cloud.
 */
class WeatherCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsweather";
    private static final String STRENGTH = "strength";
    private static final String CLEAR = "clear";
    private static final String PERCENT_PARAMETER = "percent";
    private static final String WIND = "wind";
    private static final String SPEED = "speed";
    private static final String DIRECTION = "direction";
    private static final String DEGREES_PARAMETER = "degrees";
    private static final String GROUND_FOG = "groundfog";
    private static final String FROST = "frost";
    private static final String LIGHTNING = "lightning";
    private static final String CLOUDS = "clouds";
    private static final String THICKNESS_PARAMETER = "thickness";
    // The thickest clouds can be set, in blocks
    private static final int MAX_CLOUD_THICKNESS = 120;
    // A full storm's wind is 100: thunderstorms and dust storms blow harder
    private static final int MAX_WIND_PERCENT = 300;

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND)
                .executes(ctx -> this.execute(ctx, WeatherCommandHandler::status))
                .then(ClientCommandRegistrationEvent.literal(STRENGTH)
                        .then(ClientCommandRegistrationEvent.argument(PERCENT_PARAMETER, IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> this.execute(ctx,
                                        () -> WeatherCommandHandler.strength(IntegerArgumentType.getInteger(ctx, PERCENT_PARAMETER))))))
                .then(subCommand(CLEAR, WeatherCommandHandler::clear))
                .then(this.windCommand())
                .then(this.groundFogCommand())
                .then(this.frostCommand())
                .then(this.cloudsCommand())
                .then(this.lightningCommand()));
    }

    /**
     * /dsweather lightning strikes a bolt ahead of the player; lightning ground, spider or sheet picks the kind
     */
    private LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> lightningCommand() {
        var command = ClientCommandRegistrationEvent.literal(LIGHTNING)
                .executes(ctx -> this.execute(ctx, () -> WeatherCommandHandler.lightning(Strike.Kind.GROUND)));
        for (var kind : Strike.Kind.values())
            command.then(subCommand(kind.name().toLowerCase(Locale.ROOT), () -> WeatherCommandHandler.lightning(kind)));
        return command;
    }

    /**
     * /dsweather groundfog 0-100 gives the ground fog that thickness everywhere, as a percent of the thickest; clear
     * goes back to the biomes'
     */
    private LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> groundFogCommand() {
        return ClientCommandRegistrationEvent.literal(GROUND_FOG)
                .executes(ctx -> this.execute(ctx, WeatherCommandHandler::groundFogStatus))
                .then(ClientCommandRegistrationEvent.argument(PERCENT_PARAMETER, IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> this.execute(ctx,
                                () -> WeatherCommandHandler.groundFog(IntegerArgumentType.getInteger(ctx, PERCENT_PARAMETER)))))
                .then(subCommand(CLEAR, WeatherCommandHandler::groundFogClear));
    }

    /**
     * /dsweather clouds 0-100 holds the clouds' coverage, as a percent of the sky; clouds 0-100 1-120 also the
     * thickness of the thickest, in blocks; clear goes back to the weather's
     */
    private LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> cloudsCommand() {
        return ClientCommandRegistrationEvent.literal(CLOUDS)
                .executes(ctx -> this.execute(ctx, WeatherCommandHandler::cloudsStatus))
                .then(ClientCommandRegistrationEvent.argument(PERCENT_PARAMETER, IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> this.execute(ctx,
                                () -> WeatherCommandHandler.clouds(IntegerArgumentType.getInteger(ctx, PERCENT_PARAMETER), Float.NaN)))
                        .then(ClientCommandRegistrationEvent.argument(THICKNESS_PARAMETER, IntegerArgumentType.integer(1, MAX_CLOUD_THICKNESS))
                                .executes(ctx -> this.execute(ctx,
                                        () -> WeatherCommandHandler.clouds(IntegerArgumentType.getInteger(ctx, PERCENT_PARAMETER),
                                                IntegerArgumentType.getInteger(ctx, THICKNESS_PARAMETER))))))
                .then(subCommand(CLEAR, WeatherCommandHandler::cloudsClear));
    }

    /**
     * /dsweather frost 0-100 gives every window that much frost, as a percent of the most; clear goes back to the
     * weather's
     */
    private LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> frostCommand() {
        return ClientCommandRegistrationEvent.literal(FROST)
                .executes(ctx -> this.execute(ctx, WeatherCommandHandler::frostStatus))
                .then(ClientCommandRegistrationEvent.argument(PERCENT_PARAMETER, IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> this.execute(ctx,
                                () -> WeatherCommandHandler.frost(IntegerArgumentType.getInteger(ctx, PERCENT_PARAMETER)))))
                .then(subCommand(CLEAR, WeatherCommandHandler::frostClear));
    }

    /**
     * /dsweather wind shows the wind; wind speed 0-300 sets its speed, as a percent of a full storm's wind; wind
     * direction sets the way it blows toward, as degrees clockwise from north or a compass point; wind clear lets it
     * go its own way again
     */
    private LiteralArgumentBuilder<ClientCommandRegistrationEvent.ClientCommandSourceStack> windCommand() {
        var direction = ClientCommandRegistrationEvent.literal(DIRECTION)
                .then(ClientCommandRegistrationEvent.argument(DEGREES_PARAMETER, IntegerArgumentType.integer(0, 359))
                        .executes(ctx -> this.execute(ctx,
                                () -> WeatherCommandHandler.windDirection(IntegerArgumentType.getInteger(ctx, DEGREES_PARAMETER)))));
        for (var point : Wind.compassPoints())
            direction.then(subCommand(point, () -> WeatherCommandHandler.windDirection(Wind.bearingOf(point))));

        return ClientCommandRegistrationEvent.literal(WIND)
                .executes(ctx -> this.execute(ctx, WeatherCommandHandler::windStatus))
                .then(ClientCommandRegistrationEvent.literal(SPEED)
                        .then(ClientCommandRegistrationEvent.argument(PERCENT_PARAMETER, IntegerArgumentType.integer(0, MAX_WIND_PERCENT))
                                .executes(ctx -> this.execute(ctx,
                                        () -> WeatherCommandHandler.windSpeed(IntegerArgumentType.getInteger(ctx, PERCENT_PARAMETER))))))
                .then(direction)
                .then(subCommand(CLEAR, WeatherCommandHandler::windClear));
    }
}
