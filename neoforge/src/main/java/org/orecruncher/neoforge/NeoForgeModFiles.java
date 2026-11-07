// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import org.orecruncher.dsurround.lib.resources.IModFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The installed mods' files, read through the jars NeoForge already has open (each mod file's {@link JarContents}),
 * rather than opening each again as a zip file system and keeping it open. Any thread.
 */
final class NeoForgeModFiles implements IModFiles {

    // Whether a mod has anything in a folder, once looked: looking goes through every file in the mod
    private final Map<String, Boolean> folders = new ConcurrentHashMap<>();

    @Override
    public boolean has(String modId, String path) {
        return contents(modId).map(contents -> contents.containsFile(path)).orElse(false);
    }

    @Override
    public boolean hasFolder(String modId, String folder) {
        return this.folders.computeIfAbsent(modId + "|" + folder, key -> contents(modId).map(contents -> anyIn(contents, folder)).orElse(false));
    }

    @Override
    public InputStream open(String modId, String path) throws IOException {
        var contents = contents(modId);
        if (contents.isEmpty())
            throw new NoSuchFileException(modId + ":" + path);
        return contents.get().openFile(path);
    }

    private static Optional<JarContents> contents(String modId) {
        var file = ModList.get().getModFileById(modId);
        return file == null ? Optional.empty() : Optional.of(file.getFile().getContents());
    }

    private static boolean anyIn(JarContents contents, String folder) {
        // The visit can't be stopped otherwise: the first file found is enough
        try {
            contents.visitContent(folder, (path, resource) -> {
                throw Found.INSTANCE;
            });
            return false;
        } catch (Found found) {
            return true;
        }
    }

    private static final class Found extends RuntimeException {
        static final Found INSTANCE = new Found();

        private Found() {
            super(null, null, false, false);
        }
    }
}
