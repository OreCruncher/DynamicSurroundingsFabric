package org.orecruncher.dsurround.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.BlockLibrary;
import org.orecruncher.dsurround.config.DimensionLibrary;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
class DumpCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("dsdump")
            .then(literal("biomes").executes(cmd -> dumpBiomes(cmd.getSource())))
            .then(literal("sounds").executes(cmd -> dumpSounds(cmd.getSource())))
            .then(literal("dimensions").executes(cmd -> dumpDimensions(cmd.getSource())))
            .then(literal("blockinfo").executes(cmd -> dumpBlockInfo(cmd.getSource())))
            .then(literal("blockstates").executes(cmd -> dumpBlockState(cmd.getSource())))
        );
    }

    private static int dumpBiomes(FabricClientCommandSource src) {
        return handle(src, "dump.biomes", BiomeLibrary::dumpBiomes);
    }

    private static int dumpSounds(FabricClientCommandSource src) {
        return handle(src, "dump.sounds", DumpCommand::tbd);
    }

    private static int dumpDimensions(FabricClientCommandSource src) {
        return handle(src, "dump.dimensions", DimensionLibrary::dump);
    }

    private static int dumpBlockInfo(FabricClientCommandSource src) {
        return handle(src, "dump.blockinfo", BlockLibrary::dumpBlockInfo);
    }

    private static int dumpBlockState(FabricClientCommandSource src) {
        return handle(src, "dump.blockstates", BlockLibrary::dumpBlockStates);
    }

    private static int handle(final FabricClientCommandSource source, final String cmdString, final Supplier<Stream<String>> supplier) {

        final String operation = cmdString.substring(5);
        final String fileName = operation + ".txt";
        var target = Client.DUMP_PATH.resolve(fileName).toFile();

        try {
            try (PrintStream out = new PrintStream(target)) {
                final Stream<String> stream = supplier.get();
                stream.forEach(out::println);
                out.flush();
            } catch (final Throwable t) {
                Client.LOGGER.error(t, "Error writing dump file '%s'", target.toString());
            }
            Commands.sendSuccess(source, "dump", operation, target.toString());
        } catch (final Throwable t) {
            Commands.sendFailure(source, cmdString, operation);
        }
        return 0;
    }

    private static Stream<String> tbd() {
        final List<String> result = ImmutableList.of("Not hooked up");
        return result.stream();
    }
}
