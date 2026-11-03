// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.platform;

import java.nio.file.Path;

public interface IMinecraftDirectories {
    Path getModConfigDirectory();

    Path getModDataDirectory();

    Path getModDumpDirectory();

    /**
     * Where /dsprofile writes its recordings
     */
    Path getModProfileDirectory();
}
