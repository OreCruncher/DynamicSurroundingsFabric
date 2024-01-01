package org.orecruncher.dsurround.lib.version;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.Localization;

public class VersionResult {
    public final String version;
    public final String displayName;
    public final String downloadLocation;
    public final String downloadLocationModrinth;

    VersionResult(String version, String displayName, String downloadLocation, String downloadLocationModrinth) {
        this.version = version;
        this.displayName = displayName;
        this.downloadLocation = downloadLocation;
        this.downloadLocationModrinth = downloadLocationModrinth;
    }

    public Component getChatText() {
        var formattedText = Component.translatableEscape(
                "dsurround.text.NewVersion",
                this.displayName,
                this.version,
                Localization.load("dsurround.text.NewVersion.curseforge"),
                this.downloadLocation,
                Localization.load("dsurround.text.NewVersion.modrinth"),
                this.downloadLocationModrinth).toString();

        return Component.Serializer.fromJson(formattedText);
    }
}
