package org.orecruncher.dsurround.lib.platform;

import net.fabricmc.loader.api.Version;
import net.minecraft.SharedConstants;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

public record ModInformation(String modId, String displayName, Version version, String updateUrl, String curseForgeLink, String modrinthLink) {

    public Optional<URL> getUpdateUrl() {
        try {
            return Optional.of(new URL(this.updateUrl));
        } catch (MalformedURLException ignored) {
        }
        return Optional.empty();
    }

    public String getBranding() {
        return String.format("%s %s-%s", this.displayName, SharedConstants.getGameVersion().getName(), this.version);
    }
}
