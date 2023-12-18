package org.orecruncher.dsurround.lib.infra;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

public final class ModInformation {

    private final ModMetadata _metadata;

    ModInformation(ModMetadata data) {
        this._metadata = data;
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

    public URL get_updateUrl() {
        try {
            return new URL(this._metadata.getCustomValue("updateURL").getAsString());
        } catch (MalformedURLException ignored) {
        }
        return null;
    }

    public String get_branding() {
        return String.format("%s (%s) v%s", this.get_displayName(), this.get_modId(), this.get_version());
    }
}
