package org.orecruncher.dsurround.lib.platform;

import net.minecraft.SharedConstants;
import org.orecruncher.dsurround.lib.version.SemanticVersion;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

public record ModInformation(String modId, String displayName, SemanticVersion version, String updateUrl, String curseForgeLink, String modrinthLink) {

    public Optional<URL> getUpdateUrl() {
        try {
            return Optional.of(new URL(this.updateUrl));
        } catch (MalformedURLException ignored) {
        }
        return Optional.empty();
    }

    public String getBranding() {
        return String.format("%s %s-%s", this.displayName, SharedConstants.getCurrentVersion().getName(), this.version);
    }
}
