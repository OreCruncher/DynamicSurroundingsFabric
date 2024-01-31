package org.orecruncher.dsurround.lib.resources;

import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ServerResourceLookupHelper {

    private Map<String, List<Path>> rootPaths;

    public ServerResourceLookupHelper() {
    }

    public void refresh(IPlatform platform) {
        this.rootPaths = platform.getResourceRootPaths();
    }

    public Collection<Path> findResourcePaths(String fileNamePattern) {
        return this.rootPaths.entrySet().parallelStream()
                .map(kvp -> this.findPath(fileNamePattern, kvp.getValue()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<Path> findPath(String fileNamePattern, List<Path> rootPathsToCheck) {
        if (rootPathsToCheck.size() == 1) {
            var root = rootPathsToCheck.get(0);
            Path path = root.resolve(fileNamePattern.replace("/", root.getFileSystem().getSeparator()));
            if (Files.exists(path))
                return Optional.of(path);
            return Optional.empty();
        }

        for (Path root : rootPathsToCheck) {
            Path path = root.resolve(fileNamePattern.replace("/", root.getFileSystem().getSeparator()));
            if (Files.exists(path)) {
                return Optional.of(path);
            }
        }

        return Optional.empty();
    }
}
