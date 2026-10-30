package org.orecruncher.dsurround.bench;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.orecruncher.dsurround.lib.Library;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * The development bench: scenario runs (see {@link ScenarioRunner}) and /dsbench. Only in development runs, started by
 * {@link org.orecruncher.dsurround.profiling.BenchHook}; this package isn't in the published jars.
 * <p>
 * /dsbench mark &lt;name&gt; remembers where the player stands and looks as a scene of that name in this world, for
 * scenes that aren't a biome (waterfalls, flame jets, a crowd); /dsbench list lists the world's scenes.
 */
public final class Bench {

    private Bench() {
    }

    /**
     * Called by reflection from BenchHook
     *
     * @param scenario the scenario file to run, or empty for none (only /dsbench)
     */
    public static void start(String scenario) {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommandRegistrationEvent.literal("dsbench")
                        .then(ClientCommandRegistrationEvent.literal("mark")
                                .then(ClientCommandRegistrationEvent.argument("name", StringArgumentType.word())
                                        .executes(ctx -> reply(ctx, () -> mark(StringArgumentType.getString(ctx, "name"))))))
                        .then(ClientCommandRegistrationEvent.literal("list")
                                .executes(ctx -> reply(ctx, Bench::list)))));
        if (!scenario.isBlank())
            ScenarioRunner.start(Path.of(scenario));
    }

    private static int reply(CommandContext<ClientCommandRegistrationEvent.ClientCommandSourceStack> ctx, Supplier<String> handler) {
        String message;
        try {
            message = handler.get();
        } catch (Exception e) {
            Library.LOGGER.error(e, "/dsbench failed");
            message = "Failed: " + e;
        }
        ctx.getSource().arch$getPlayer().sendSystemMessage(Component.literal(message));
        return 0;
    }

    private static String mark(String name) {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var player = mc.player;
        if (server == null || player == null || mc.level == null)
            return "Scenes are kept in single player worlds only";
        var biome = mc.level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
        var location = new SceneLocations.Location(biome, mc.level.dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), 1D, true);
        try {
            var locations = SceneLocations.load(server.getWorldPath(LevelResource.ROOT));
            locations.put(name, location, server.getWorldGenSettings().options().seed());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save the scene: " + e, e);
        }
        return String.format("Scene '%s' marked at %.1f %.1f %.1f (yaw %.1f, pitch %.1f) in %s", name, location.x(),
                location.y(), location.z(), location.yaw(), location.pitch(), biome);
    }

    private static String list() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null)
            return "Scenes are kept in single player worlds only";
        try {
            var locations = SceneLocations.load(server.getWorldPath(LevelResource.ROOT));
            if (locations.all().isEmpty())
                return "No scenes in this world yet";
            var text = new StringBuilder("Scenes in this world:");
            locations.all().forEach((name, l) -> text.append(String.format("%n  %s: %s at %.0f %.0f %.0f%s", name, l.biome(),
                    l.x(), l.y(), l.z(), l.marked() ? " (marked)" : String.format(" (found, %.0f%% biome)", 100 * l.biomeShare()))));
            return text.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the scenes: " + e, e);
        }
    }
}
