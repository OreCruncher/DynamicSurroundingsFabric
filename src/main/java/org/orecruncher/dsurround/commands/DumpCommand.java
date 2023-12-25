package org.orecruncher.dsurround.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import org.orecruncher.dsurround.config.libraries.*;
import org.orecruncher.dsurround.lib.commands.client.ClientCommand;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class DumpCommand extends ClientCommand {

    private static final IMinecraftDirectories directories = ContainerManager.resolve(IMinecraftDirectories.class);

    DumpCommand() {
        super("dsdump");
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(this.command)
                .then(
                        literal("biomes")
                                .executes(cmd -> execute(cmd, DumpCommandHandler::dumpBiomes)))
                .then(
                        literal("sounds")
                                .executes(cmd -> execute(cmd, DumpCommandHandler::dumpSounds)))
                .then(
                        literal("dimensions")
                                .executes(cmd -> execute(cmd, DumpCommandHandler::dumpDimensions)))
                .then(
                        literal("blocks")
                                .executes(cmd -> execute(cmd, () -> DumpCommandHandler.dumpBlocks(false)))
                            .then(literal("nostates")
                                .executes(cmd -> execute(cmd, () -> DumpCommandHandler.dumpBlocks(true)))))
                .then(
                        literal("blocksbytag")
                                .executes(cmd -> execute(cmd, DumpCommandHandler::dumpBlocksByTag)))
                .then(
                        literal("blockconfigrules")
                                .executes(cmd -> execute(cmd, DumpCommandHandler::dumpBlockConfigRules)))
                .then(
                        literal("blockstates")
                                .executes(cmd -> execute(cmd, DumpCommandHandler::dumpBlockState)))
                .then(
                        literal("items")
                                .executes(cmd -> execute(cmd, DumpCommandHandler::dumpItems)))
        );
    }

    private int execute(CommandContext<FabricClientCommandSource> ctx, Supplier<Text> commandHandler) {
        var result = commandHandler.get();
        ctx.getSource().sendFeedback(result);
        return 0;
    }

    private int dumpBiomes(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBiomeLibrary.class);
        return handle(src, "dump.biomes", library::dump);
    }

    private int dumpSounds(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(ISoundLibrary.class);
        return handle(src, "dump.sounds", library::dump);
    }

    private int dumpDimensions(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IDimensionLibrary.class);
        return handle(src, "dump.dimensions", library::dump);
    }

    private int dumpBlockConfigRules(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blockconfigrules", library::dumpBlockConfigRules);
    }

    private int dumpBlockState(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blockstates", library::dumpBlockStates);
    }

    private int dumpBlocks(FabricClientCommandSource src, boolean noStates) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blocks", () -> library.dumpBlocks(noStates));
    }

    private int dumpBlocksByTag(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IBlockLibrary.class);
        return handle(src, "dump.blocksbytag", library::dump);
    }

    private int dumpItems(FabricClientCommandSource src) {
        var library = ContainerManager.resolve(IItemLibrary.class);
        return handle(src, "dump.items", library::dump);
    }

    private int handle(final FabricClientCommandSource source, final String cmdString, final Supplier<Stream<String>> supplier) {

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
            this.sendSuccess(source, operation, target.toString());
        } catch (final Throwable t) {
            this.sendFailure(source, cmdString, operation);
        }
        return 0;
    }

    private static Stream<String> tbd() {
        final List<String> result = ImmutableList.of("Not hooked up");
        return result.stream();
    }
}
