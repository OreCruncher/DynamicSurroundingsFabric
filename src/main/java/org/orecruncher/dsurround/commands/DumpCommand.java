package org.orecruncher.dsurround.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.libraries.*;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class DumpCommand {

    private static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);
    private static final IMinecraftDirectories directories = ContainerManager.resolve(IMinecraftDirectories.class);

    public static void register(@Nullable CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.register(literal("dsdump")
                .then(literal("biomes").executes(cmd -> dumpBiomes(cmd.getSource())))
                .then(literal("sounds").executes(cmd -> dumpSounds(cmd.getSource())))
                .then(literal("dimensions").executes(cmd -> dumpDimensions(cmd.getSource())))
                .then(literal("blocks")
                        .executes(cmd -> dumpBlocks(cmd.getSource(), false))
                        .then(literal("nostates")
                        .executes(cmd -> dumpBlocks(cmd.getSource(), true))))
                .then(literal("blocksbytag").executes(cmd -> dumpBlocksByTag(cmd.getSource())))
                .then(literal("blockconfigrules").executes(cmd -> dumpBlockConfigRules(cmd.getSource())))
                .then(literal("blockstates").executes(cmd -> dumpBlockState(cmd.getSource())))
                .then(literal("items").executes(cmd -> dumpItems(cmd.getSource())))
        );
    }

    private static int dumpBiomes(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBiomeLibrary.class);
        return handle(src, "dump.biomes", library::dump);
    }

    private static int dumpSounds(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(ISoundLibrary.class);
        return handle(src, "dump.sounds", library::dump);
    }

    private static int dumpDimensions(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IDimensionLibrary.class);
        return handle(src, "dump.dimensions", library::dump);
    }

    private static int dumpBlockConfigRules(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blockconfigrules", library::dumpBlockConfigRules);
    }

    private static int dumpBlockState(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blockstates", library::dumpBlockStates);
    }

    private static int dumpBlocks(FabricClientCommandSource src, boolean noStates) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blocks", () -> library.dumpBlocks(noStates));
    }

    private static int dumpBlocksByTag(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blocksbytag", library::dump);
    }

    private static int dumpItems(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IItemLibrary.class);
        return handle(src, "dump.items", library::dump);
    }

    private static int handle(final FabricClientCommandSource source, final String cmdString, final Supplier<Stream<String>> supplier) {

        final String operation = cmdString.substring(5);
        final String fileName = operation + ".txt";
        var target = directories.getModDumpDirectory().resolve(fileName).toFile();

        try {
            try (PrintStream out = new PrintStream(target)) {
                final Stream<String> stream = supplier.get();
                stream.forEach(out::println);
                out.flush();
            } catch (final Throwable t) {
                LOGGER.error(t, "Error writing dump file '%s'", target.toString());
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
