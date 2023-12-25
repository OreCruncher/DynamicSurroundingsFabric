package org.orecruncher.dsurround.lib.util;

import java.nio.file.Path;

public interface IMinecraftDirectories {
    Path getModConfigDirectory();

    Path getModDataDirectory();

    Path getModDumpDirectory();
}
