package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.function.SingletonSupplier;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Finds data files, such as tags, in the jars of the installed mods (data/(namespace)/...). When connected to a
 * server that doesn't send the mod's tags (a vanilla server), this is where they are filled in from.
 */
public class ServerResourceFinder extends AbstractResourceFinder {

    // The installed mods' data folders. Mods can't come or go during a session, so these are found once.
    private static final Supplier<List<Path>> INSTALLED_DATA_ROOTS = SingletonSupplier.from(ServerResourceFinder::findInstalledDataRoots);

    private final Supplier<? extends Collection<Path>> dataRoots;

    protected ServerResourceFinder(IModLog logger) {
        this(logger, INSTALLED_DATA_ROOTS);
    }

    /**
     * @param dataRoots the data folders to look in
     */
    ServerResourceFinder(IModLog logger, Supplier<? extends Collection<Path>> dataRoots) {
        super(logger);
        this.dataRoots = dataRoots;
    }

    /**
     * @param assetPath what to find, as namespace:path (e.g. "c:tags/block/glass_blocks")
     */
    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String assetPath) {
        var location = ResourceLocation.tryParse(assetPath);
        if (location == null) {
            this.logger.warn("Not a resource location: %s", assetPath);
            return List.of();
        }

        var relative = withJsonExtension(location.getNamespace() + "/" + location.getPath());
        var results = new ObjectArray<DiscoveredResource<T>>();
        for (var root : this.dataRoots.get()) {
            var file = root.resolve(relative.replace("/", root.getFileSystem().getSeparator()));
            if (Files.exists(file))
                this.readInto(location, location.getNamespace(), file, () -> Files.newInputStream(file), codec, results);
        }
        return results;
    }

    private static List<Path> findInstalledDataRoots() {
        var folder = PackType.SERVER_DATA.getDirectory();
        var roots = new ObjectArray<Path>();
        for (var mod : Platform.getMods()) {
            // A mod may be spread over several roots (in development, its classes and resources); the first with a
            // data folder is the one
            for (var root : mod.getFilePaths()) {
                var data = root.resolve(folder);
                if (Files.exists(data)) {
                    roots.add(data);
                    break;
                }
            }
        }
        return List.copyOf(roots);
    }
}
