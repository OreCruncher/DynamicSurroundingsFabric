package org.orecruncher.dsurround.lib.util;

import java.nio.file.Path;

public interface IMinecraftDirectories {
    Path getGameDirectory();

    Path getConfigDirectory();

    Path getCrashReportDirectory();
}
