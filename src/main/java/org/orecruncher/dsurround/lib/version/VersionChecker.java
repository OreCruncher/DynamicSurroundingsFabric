package org.orecruncher.dsurround.lib.version;

import org.orecruncher.dsurround.lib.CodecExtensions;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import net.fabricmc.loader.api.Version;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.infra.ModInformation;
import org.orecruncher.dsurround.lib.logging.IModLog;

public class VersionChecker implements IVersionChecker {

    private final IModLog logger;
    private final String displayName;
    private final Version modVersion;
    private final URL updateURL;

    public VersionChecker(ModInformation modInformation, IModLog logger) {
        this.logger = logger;
        this.displayName = modInformation.get_displayName();
        this.modVersion = modInformation.get_version();
        this.updateURL = modInformation.get_updateUrl();
    }

    @Override
    public Optional<VersionResult> getUpdateText() {
        return this.getVersionInformation().flatMap(this::getUpdateText);
    }

    private Optional<VersionInformation> getVersionInformation() {
        return this.getVersionData().flatMap(c -> CodecExtensions.deserialize(c, VersionInformation.CODEC));
    }

    private Optional<String> getVersionData() {
        try (InputStream in = this.updateURL.openStream()) {
            byte[] bytes = in.readAllBytes();
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            this.logger.error(t, "Unable to fetch version information from %s", this.updateURL);
        }
        return Optional.empty();
    }

    private Optional<VersionResult> getUpdateText(VersionInformation info) {
        var result = info.getNewestVersion(FrameworkUtils.getMinecraftVersion(), this.modVersion);
        if (result.isEmpty())
            return Optional.empty();

        var version = result.get().getFirst();
        return Optional.of(new VersionResult(version, this.displayName, info.downloadLocation, info.downloadLocationModrinth));
    }
}
