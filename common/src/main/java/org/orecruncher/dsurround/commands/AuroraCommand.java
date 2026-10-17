package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.commands.handlers.AuroraCommandHandler;
import org.orecruncher.dsurround.effects.aurora.AuroraActivity;
import org.orecruncher.dsurround.effects.aurora.AuroraColour;
import org.orecruncher.dsurround.effects.aurora.RealisticAurora;

import java.util.Locale;

/**
 * /dsaurora shows tonight's realistic aurora; /dsaurora activity quiet|moderate|active|storm makes it that active, and
 * /dsaurora phase growth|breakup|expansion|recovery holds it at that point in its night, and /dsaurora colour
 * normal|red|pink|crimson gives it that colour character, until /dsaurora clear.
 */
class AuroraCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsaurora";
    private static final String ACTIVITY = "activity";
    private static final String PHASE = "phase";
    private static final String COLOUR = "colour";
    private static final String CLEAR = "clear";

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        var activity = ClientCommandRegistrationEvent.literal(ACTIVITY);
        for (var value : AuroraActivity.values())
            activity.then(subCommand(value.name().toLowerCase(Locale.ROOT), () -> AuroraCommandHandler.activity(value)));

        var phase = ClientCommandRegistrationEvent.literal(PHASE);
        for (var value : RealisticAurora.Phase.values())
            phase.then(subCommand(value.name().toLowerCase(Locale.ROOT), () -> AuroraCommandHandler.phase(value)));

        var colour = ClientCommandRegistrationEvent.literal(COLOUR);
        for (var value : AuroraColour.values())
            colour.then(subCommand(value.name().toLowerCase(Locale.ROOT), () -> AuroraCommandHandler.colour(value)));

        dispatcher.register(ClientCommandRegistrationEvent.literal(COMMAND)
                .executes(ctx -> this.executeAll(ctx, AuroraCommandHandler::status))
                .then(activity)
                .then(phase)
                .then(colour)
                .then(subCommand(CLEAR, AuroraCommandHandler::clear)));
    }
}
