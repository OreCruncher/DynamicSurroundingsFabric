package org.orecruncher.dsurround.lib.infra;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

public final class ModInformation {

    private final ModMetadata _metadata;
    private final String updateURL;
    private final String curseForgeLink;
    private final String modrinthLink;

    ModInformation(ModMetadata metadata) {
        this._metadata = metadata;

        var data = this._metadata.getCustomValue("dsurround").getAsObject();
        this.updateURL = data.get("updateURL").getAsString();
        this.curseForgeLink = data.get("curseForgeLink").getAsString();
        this.modrinthLink = data.get("modrinthLink").getAsString();
    }

    @Nullable
    public static ModInformation getModInformation(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId);
        return container.map(c -> new ModInformation(c.getMetadata())).orElse(null);
    }

    public String get_modId() {
        return this._metadata.getId();
    }

    public String get_displayName() {
        return this._metadata.getName();
    }

    public Version get_version() {
        return this._metadata.getVersion();
    }

    public String get_curseForgeLink() {
        return this.curseForgeLink;
    }

    public String get_modrinthLink() {
        return this.modrinthLink;
    }

    public URL get_updateUrl() {
        try {
            return new URL(this.updateURL);
        } catch (MalformedURLException ignored) {
        }
        return null;
    }

    public String get_branding() {
        return String.format("%s %s-%s", this.get_displayName(), SharedConstants.getGameVersion().getName(), this.get_version());
    }
}
