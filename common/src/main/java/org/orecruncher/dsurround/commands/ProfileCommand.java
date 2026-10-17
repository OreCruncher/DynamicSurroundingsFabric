package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import org.orecruncher.dsurround.profiling.ProfileScene;
import org.orecruncher.dsurround.profiling.Profiler;

/**
 * /dsprofile [scene] [seconds]: sets up a scene and records the game with Java Flight Recorder while it shows (see
 * {@link Profiler}), for the player to send to the mod's authors when something is slow. With no scene, the game as it
 * is ({@link ProfileScene#GENERAL}); with no length, two minutes.
 */
class ProfileCommand extends AbstractClientCommand {

    private static final String COMMAND = "dsprofile";
    private static final String SECONDS = "seconds";
    private static final int DEFAULT_SECONDS = 120;
    private static final int LEAST_SECONDS = 10;
    private static final int MOST_SECONDS = 600;

    @Override
    public void register(CommandDispatcher<ClientCommandRegistrationEvent.ClientCommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        var command = ClientCommandRegistrationEvent.literal(COMMAND)
                .executes(ctx -> this.executeAll(ctx, () -> Profiler.start(ProfileScene.GENERAL, DEFAULT_SECONDS)));
        for (var scene : ProfileScene.values()) {
            command.then(ClientCommandRegistrationEvent.literal(scene.commandName())
                    .executes(ctx -> this.executeAll(ctx, () -> Profiler.start(scene, DEFAULT_SECONDS)))
                    .then(ClientCommandRegistrationEvent.argument(SECONDS, IntegerArgumentType.integer(LEAST_SECONDS, MOST_SECONDS))
                            .executes(ctx -> this.executeAll(ctx,
                                    () -> Profiler.start(scene, IntegerArgumentType.getInteger(ctx, SECONDS))))));
        }
        dispatcher.register(command);
    }
}
