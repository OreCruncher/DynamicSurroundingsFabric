// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.resources;

import java.io.IOException;
import java.io.InputStream;

/**
 * The files in the installed mods, by mod id, with paths relative to a mod's root and separated by "/" (as
 * {@code data/c/tags/block/glass_blocks.json}). The installed mods' are registered with the dependency container:
 * read through what the loader already has open where it can (see {@link ModFiles}), rather than opening every mod's
 * jar again and keeping it open. Any thread.
 */
public interface IModFiles {

    /**
     * Whether the mod has the file
     */
    boolean has(String modId, String path);

    /**
     * Whether the mod has anything in the folder
     */
    boolean hasFolder(String modId, String folder);

    /**
     * Opens the mod's file
     *
     * @throws IOException if it isn't there or can't be read
     */
    InputStream open(String modId, String path) throws IOException;
}
