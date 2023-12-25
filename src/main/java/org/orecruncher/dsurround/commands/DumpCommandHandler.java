package org.orecruncher.dsurround.commands;

import com.google.common.collect.ImmutableList;
import net.minecraft.text.Text;
import org.orecruncher.dsurround.config.libraries.*;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;

import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DumpCommandHandler {

    private static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);
    private static final IMinecraftDirectories directories = ContainerManager.resolve(IMinecraftDirectories.class);
    private static final IBiomeLibrary biomeLibrary = ContainerManager.resolve(IBiomeLibrary.class);
    private static final ISoundLibrary soundLibrary = ContainerManager.resolve(ISoundLibrary.class);
    private static final IDimensionLibrary dimensionLibrary = ContainerManager.resolve(IDimensionLibrary.class);
    private static final IBlockLibrary blockLibrary = ContainerManager.resolve(IBlockLibrary.class);
    private static final IItemLibrary itemLibrary = ContainerManager.resolve(IItemLibrary.class);

    public static Text dumpBiomes() {
        return handle("biomes", biomeLibrary::dump);
    }

    public static Text dumpSounds() {
        return handle("sounds", soundLibrary::dump);
    }

    public static Text dumpDimensions() {
        return handle("dimensions", dimensionLibrary::dump);
    }

    public static Text dumpBlockConfigRules() {
        return handle("blockconfigrules", blockLibrary::dumpBlockConfigRules);
    }

    public static Text dumpBlockState() {
        return handle("blockstates", blockLibrary::dumpBlockStates);
    }

    public static Text dumpBlocks(boolean noStates) {
        return handle("blocks", () -> blockLibrary.dumpBlocks(noStates));
    }

    public static Text dumpBlocksByTag() {
        return handle("blocksbytag", blockLibrary::dump);
    }

    public static Text dumpItems() {
        return handle("items", itemLibrary::dump);
    }

    private static Text handle(final String operation, final Supplier<Stream<String>> supplier) {

        final String fileName = operation + ".txt";
        var target = directories.getModDumpDirectory().resolve(fileName).toFile();

        try {
            try (PrintStream out = new PrintStream(target)) {
                final Stream<String> stream = supplier.get();
                stream.forEach(out::println);
                out.flush();
            }
            return Text.stringifiedTranslatable("dsurround.command.dsdump.success", operation, target.toString());
        } catch (final Throwable t) {
            LOGGER.error(t, "Error writing dump file '%s'", target.toString());
            return Text.stringifiedTranslatable("dsurround.command.dsdump.failure", operation, t.getMessage());
        }
    }

    private static Stream<String> tbd() {
        final List<String> result = ImmutableList.of("Not hooked up");
        return result.stream();
    }
}
