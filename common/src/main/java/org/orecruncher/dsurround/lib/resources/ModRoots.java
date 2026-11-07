// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.resources;

import dev.architectury.platform.Mod;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a mod's files are: the roots its paths lead to, for {@link ModFiles.PathBased}. A mod's path is a folder (in a
 * development run, or a jar's root on Fabric) or a jar file, which is opened as a zip file system. Resolving a file
 * against a jar's path directly, as Architectury's {@code Mod.findResource} does, looks for it beside the jar, inside a
 * folder named for it, so it never finds one in the jar.
 * <p>
 * NeoForge hands over jar files, but reads through the jars it has open instead (see {@link ModFiles}): a jar
 * opened here is kept open for the session, which for every mod in a large pack is a lot of heap.
 */
public final class ModRoots {

    // Jars opened, kept open while the game runs, as the loader keeps them
    private static final Map<Path, Optional<Path>> JARS = new ConcurrentHashMap<>();

    private ModRoots() {
    }

    public static List<Path> of(Mod mod) {
        return of(mod.getFilePaths());
    }

    /**
     * The roots paths lead to: a folder as it is, a jar opened; those that are neither, or don't open, left out
     */
    public static List<Path> of(Collection<Path> paths) {
        var result = new ArrayList<Path>();
        for (var path : paths) {
            if (Files.isDirectory(path))
                result.add(path);
            else if (Files.isRegularFile(path))
                JARS.computeIfAbsent(path, ModRoots::open).ifPresent(result::add);
        }
        return result;
    }

    // Not closed: the root is cached in JARS and read through for as long as the game runs
    @SuppressWarnings("resource")
    private static Optional<Path> open(Path jar) {
        try {
            var fileSystem = FileSystems.newFileSystem(jar);
            return Optional.of(fileSystem.getPath("/"));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
