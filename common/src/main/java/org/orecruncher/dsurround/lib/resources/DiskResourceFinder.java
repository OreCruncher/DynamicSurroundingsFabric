package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;


public class DiskResourceFinder extends AbstractResourceFinder {

    private final Collection<Path> namespacesOnDisk = new ObjectArray<>();

    public DiskResourceFinder(IModLog logger, Path diskLocation) {
        this(logger, diskLocation, Platform::isModLoaded);
    }

    /**
     * @param isModLoaded whether a mod is installed, by its ID: only the folders of installed mods are read
     */
    DiskResourceFinder(IModLog logger, Path diskLocation, Predicate<String> isModLoaded) {
        super(logger);

        // List the folders on disk and validate against loaded mods. If the location doesn't exist there is nothing
        // to read, which is the usual case.
        try (var directoryList = Files.newDirectoryStream(diskLocation, Files::isDirectory)) {
            directoryList.forEach(p -> {
                var modNamespace = p.getFileName().toString();
                if (isModLoaded.test(modNamespace))
                    this.namespacesOnDisk.add(p);
            });
        } catch (Throwable ignored) {
        }
    }

    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String assetPath) {
        // The usual case: no configuration of the player's own
        if (this.namespacesOnDisk.isEmpty())
            return List.of();

        var fileName = withJsonExtension(assetPath);
        var results = new ObjectArray<DiscoveredResource<T>>();
        for (var folder : this.namespacesOnDisk) {
            var file = folder.resolve(fileName);
            if (Files.exists(file)) {
                var namespace = folder.getFileName().toString();
                var location = ResourceLocation.fromNamespaceAndPath(namespace, assetPath);
                this.readInto(location, namespace, file, () -> Files.newInputStream(file), codec, results);
            }
        }
        return results;
    }
}