package org.orecruncher.dsurround.lib.resources;

import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public class ResourceLookupHelper {

    private final PackType packType;

    private Collection<Path> rootPaths;

    public ResourceLookupHelper(PackType packType) {
        this.packType = packType;
    }

    public void refresh(IPlatform platform) {
        this.rootPaths = platform.getResourceRootPaths(this.packType);
    }

    public Collection<Path> findResourcePaths(String fileNamePattern) {
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
}