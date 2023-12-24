package org.orecruncher.dsurround.lib.platform;

import net.fabricmc.loader.api.Version;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public interface IPlatform {

    Optional<ModInformation> getModInformation(String modId);

    Optional<String> getModDisplayName(String namespace);

    default Optional<Version> getMinecraftVersion() {
        return getModVersion("minecraft");
    }

    Optional<Version> getModVersion(String namespace);

    boolean isModLoaded(String namespace);

    Collection<String> getModIdList(boolean loadedOnly);

    /**
     * Gets the path to a mod's configuration directory. If it doesn't exist it will be created.  If for some reason
     * it cannot be created, the standard Minecraft config path will be returned.
     *
     * @param modId ModId to obtain the configuration path.
     * @return Path to the mod's configuration directory.
     */
    Path getConfigPath(final String modId);
}
