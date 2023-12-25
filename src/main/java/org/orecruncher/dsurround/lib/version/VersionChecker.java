package org.orecruncher.dsurround.lib.version;

import org.orecruncher.dsurround.lib.CodecExtensions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.logging.IModLog;

public class VersionChecker implements IVersionChecker {

    private final IModLog logger;
    private final ModInformation modInfo;

    public VersionChecker(ModInformation modInformation, IModLog logger) {
        this.logger = logger;
        this.modInfo = modInformation;
    }

    @Override
    public Optional<VersionResult> getUpdateText() {
        return this.getVersionInformation().flatMap(this::getUpdateText);
    }

    private Optional<VersionInformation> getVersionInformation() {
        return this.getVersionData().flatMap(c -> CodecExtensions.deserialize(c, VersionInformation.CODEC));
    }

    private Optional<String> getVersionData() {
        return this.modInfo.getUpdateUrl()
                .map(url -> {
                    try (InputStream in = url.openStream()) {
                        byte[] bytes = in.readAllBytes();
                        return new String(bytes, StandardCharsets.UTF_8);
                    } catch (Throwable t) {
                        this.logger.error(t, "Unable to fetch version information from %s", this.modInfo.getUpdateUrl());
                    }
                    return null;
                });
    }

    private Optional<VersionResult> getUpdateText(VersionInformation info) {
        var mcVersion = ContainerManager.resolve(IPlatform.class).getMinecraftVersion();
        if (mcVersion.isPresent()) {
            var newest = info.getNewestVersion(mcVersion.get(), this.modInfo.version());
            if (newest.isPresent()) {
                var version = newest.get().getFirst();
                return Optional.of(new VersionResult(version, this.modInfo.displayName(), this.modInfo.curseForgeLink(), this.modInfo.modrinthLink()));
            }
        }
        return Optional.empty();
    }
}
