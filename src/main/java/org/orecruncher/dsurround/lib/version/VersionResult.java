package org.orecruncher.dsurround.lib.version;

import net.fabricmc.loader.api.Version;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.Localization;

public class VersionResult {
    public final Version version;
    public final String displayName;
    public final String downloadLocation;
    public final String downloadLocationModrinth;

    VersionResult(Version version, String displayName, String downloadLocation, String downloadLocationModrinth) {
        this.version = version;
        this.displayName = displayName;
        this.downloadLocation = downloadLocation;
        this.downloadLocationModrinth = downloadLocationModrinth;
    }

    public Text getChatText() {
        var key = StringUtils.isEmpty(this.downloadLocationModrinth) ? "dsurround.text.NewVersion" : "dsurround.text.NewVersion.cfNmr";
        var formattedText = I18n.translate(
                key,
                this.displayName,
                this.version.getFriendlyString(),
                Localization.load("dsurround.text.NewVersion.curseforge"),
                this.downloadLocation,
                Localization.load("dsurround.text.NewVersion.modrinth"),
                this.downloadLocationModrinth);

        return Text.Serialization.fromJson(formattedText);
    }
}
