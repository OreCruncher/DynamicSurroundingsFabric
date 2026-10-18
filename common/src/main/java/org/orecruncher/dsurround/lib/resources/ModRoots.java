package org.orecruncher.dsurround.lib.resources;

import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.lib.Library;

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
 * Where a mod's files are: the roots its paths lead to. A mod's path is a folder (in a development run, or a jar's
 * root on Fabric, and on NeoForge for 1.21.1) or a jar file (NeoForge for 26.2), which is opened as a zip file system.
 * Resolving a file against a jar's path directly, as Architectury's {@code Mod.findResource} does, looks for it beside
 * the jar, inside a folder named for it, so it never finds one in the jar.
 */
public final class ModRoots {

    // Jars opened, kept open while the game runs, as the loader keeps them
    private static final Map<Path, Optional<Path>> JARS = new ConcurrentHashMap<>();

    private ModRoots() {
    }

    /**
     * The roots of the mod whose id is the namespace, if it has the namespace's data, else of the first mod that does.
     * Logged, once a namespace: where none are found, its biomes' traits are only guessed.
     */
    public static List<Path> forNamespace(String namespace) {
        var data = "data/" + namespace;
        var owner = Platform.getOptionalMod(namespace).filter(mod -> holds(of(mod), data))
                .or(() -> Platform.getMods().stream().filter(mod -> holds(of(mod), data)).findFirst());
        if (owner.isEmpty()) {
            Library.LOGGER.info("No world generation files for %s in any mod: its biomes' traits aren't read from their definitions", namespace);
            return List.of();
        }
        Library.LOGGER.info("World generation files for %s are read from %s", namespace, owner.get().getModId());
        return of(owner.get());
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

    private static Optional<Path> open(Path jar) {
        try {
            var fileSystem = FileSystems.newFileSystem(jar);
            return Optional.of(fileSystem.getPath("/"));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean holds(List<Path> roots, String folder) {
        return roots.stream().anyMatch(root -> Files.isDirectory(root.resolve(folder)));
    }

    /**
     * The first of the roots holding a file
     */
    public static Optional<Path> find(List<Path> roots, String file) {
        return roots.stream().map(root -> root.resolve(file)).filter(Files::isRegularFile).findFirst();
    }
}
