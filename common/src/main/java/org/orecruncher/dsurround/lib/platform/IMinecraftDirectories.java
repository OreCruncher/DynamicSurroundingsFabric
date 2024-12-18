package org.orecruncher.dsurround.lib.platform;

import java.nio.file.Path;

public interface IMinecraftDirectories {
    Path getModConfigDirectory();

    Path getModDataDirectory();

    Path getModDumpDirectory();
}
