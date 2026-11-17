// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.resources;

import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Finding which mod a namespace's data is in, and reading mods' files through the paths the loader gives them.
 * <p>
 * The installed mods' files are the {@link IModFiles} registered with the dependency container: {@link #byPaths} by
 * default. A platform whose loader hands over a mod's jar file rather than a folder in it registers one reading through
 * the jar the loader already has open, as NeoForge does. Opening each jar again as a zip file system, and keeping it
 * open, cost about 300 bytes of heap a file in the jars for the whole session (36 MB for a pack of 120 mods), for work
 * done while loading.
 */
public final class ModFiles {

    // The mod each namespace's data was found in, once looked for
    private static final Map<String, Optional<String>> OWNERS = new ConcurrentHashMap<>();

    private ModFiles() {
    }

    /**
     * The installed mods' files, read through the paths the loader gives each mod (see {@link ModRoots}): folders on
     * Fabric and in development runs
     */
    public static IModFiles byPaths() {
        return new PathBased(id -> Platform.getOptionalMod(id).map(ModRoots::of).orElse(List.of()));
    }

    /**
     * The mod a namespace's data is in: the mod whose id is the namespace, if it has the namespace's data, else the
     * first mod that does. Logged, once a namespace: where none is found, its biomes' traits are only guessed.
     */
    public static Optional<String> ownerOf(String namespace) {
        return OWNERS.computeIfAbsent(namespace, ModFiles::findOwner);
    }

    private static Optional<String> findOwner(String namespace) {
        var data = "data/" + namespace;
        var files = ContainerManager.resolve(IModFiles.class);
        var owner = Platform.getOptionalMod(namespace).map(Mod::getModId).filter(id -> files.hasFolder(id, data))
                .or(() -> Platform.getMods().stream().map(Mod::getModId).filter(id -> files.hasFolder(id, data)).findFirst());
        if (owner.isEmpty())
            Library.LOGGER.info("No world generation files for %s in any mod: its biomes' traits aren't read from their definitions", namespace);
        else
            Library.LOGGER.info("World generation files for %s are read from %s", namespace, owner.get());
        return owner;
    }

    /**
     * Files read through each mod's roots, as {@link ModRoots} finds them
     */
    public static final class PathBased implements IModFiles {

        private final Function<String, List<Path>> rootsOf;

        /**
         * @param rootsOf a mod's roots, by its id
         */
        public PathBased(Function<String, List<Path>> rootsOf) {
            this.rootsOf = rootsOf;
        }

        @Override
        public boolean has(String modId, String path) {
            return this.find(modId, path).isPresent();
        }

        @Override
        public boolean hasFolder(String modId, String folder) {
            return this.rootsOf.apply(modId).stream().anyMatch(root -> Files.isDirectory(resolve(root, folder)));
        }

        @Override
        public InputStream open(String modId, String path) throws IOException {
            var file = this.find(modId, path);
            if (file.isEmpty())
                throw new NoSuchFileException(modId + ":" + path);
            return Files.newInputStream(file.get());
        }

        private Optional<Path> find(String modId, String path) {
            return this.rootsOf.apply(modId).stream().map(root -> resolve(root, path)).filter(Files::isRegularFile).findFirst();
        }

        private static Path resolve(Path root, String path) {
            return root.resolve(path.replace("/", root.getFileSystem().getSeparator()));
        }
    }
}
