package org.orecruncher.dsurround.lib.version;

import org.orecruncher.dsurround.lib.CodecExtensions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.infra.ModInformation;
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
        var url = this.modInfo.get_updateUrl();
        if (url != null) {
            try (InputStream in = this.modInfo.get_updateUrl().openStream()) {
                byte[] bytes = in.readAllBytes();
                return Optional.of(new String(bytes, StandardCharsets.UTF_8));
            } catch (Throwable t) {
                this.logger.error(t, "Unable to fetch version information from %s", this.modInfo.get_updateUrl());
            }
        }
        return Optional.empty();
    }

    private Optional<VersionResult> getUpdateText(VersionInformation info) {
        var result = info.getNewestVersion(FrameworkUtils.getMinecraftVersion(), this.modInfo.get_version());
        if (result.isEmpty())
            return Optional.empty();

        var version = result.get().getFirst();
        return Optional.of(new VersionResult(version, this.modInfo.get_displayName(), this.modInfo.get_curseForgeLink(), this.modInfo.get_modrinthLink()));
    }
}
