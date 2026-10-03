package org.orecruncher.dsurround.lib.version;

import java.util.Optional;

public interface IVersionChecker {

    /**
     * Fetches the mod's version information and compares it with the installed version. This goes over the
     * network, so it should not be called on the render thread.
     *
     * @return the result, or empty if there is no recommended release for this Minecraft version
     * @throws VersionCheckException if the information couldn't be fetched or read
     */
    Optional<VersionResult> getVersionResult();
}
