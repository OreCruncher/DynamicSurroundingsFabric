package org.orecruncher.dsurround.lib.resources;

import com.google.common.collect.ImmutableList;
import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.lib.Library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public class ResourceLookupHelper {

    private final PackType packType;

    private Collection<Path> rootPaths;

    public ResourceLookupHelper(PackType packType) {
        this.packType = packType;
        this.rootPaths = ImmutableList.of();
    }

    public void refresh() {
        this.rootPaths = this.getResourceRootPaths();
    }

    public Collection<Path> findResourcePaths(String fileNamePattern) {
        if (this.rootPaths.isEmpty())
            Library.LOGGER.warn("No root paths defined for ResourceLookupHelper");

        return this.rootPaths.stream()
                .map(path -> this.findPath(fileNamePattern, path))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<Path> findPath(String fileNamePattern, Path root) {
        Path path = root.resolve(fileNamePattern.replace("/", root.getFileSystem().getSeparator()));
        if (Files.exists(path))
            return Optional.of(path);
        return Optional.empty();
    }

    private Collection<Path> getResourceRootPaths() {
        var pathPrefix = this.packType.getDirectory();
        return Platform.getMods()
                .stream()
                .map(mod -> findPath(mod, pathPrefix))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    static private Optional<Path> findPath(Mod container, String file)
    {
        for (Path root : container.getFilePaths()) {
            Path path = root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
            if (Files.exists(path))
                return Optional.of(path);
        }

        return Optional.empty();
    }
}