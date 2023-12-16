package org.orecruncher.dsurround.lib.version;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.CodecExtensions;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import net.fabricmc.loader.api.Version;

public class VersionChecker {

    public static Optional<String> getVersionData(URL url) {
        try (InputStream in = url.openStream()) {
            byte[] bytes = in.readAllBytes();
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Unable to fetch version information from %s", url);
        }
        return Optional.empty();
    }

    public static Optional<VersionInformation> getVersionInformation(URL url) {
        var content = getVersionData(url);
        if (content.isPresent()) {
            return CodecExtensions.deserialize(content.get(), VersionInformation.CODEC);
        }
        return Optional.empty();
    }

    public static Optional<VersionResult> getUpdateText(String displayName, Version minecraftVersion, Version modVersion, VersionInformation info) {
        // Sanitize the display name to remove any formatting
        displayName = Formatting.strip(displayName);

        var result = info.getNewestVersion(minecraftVersion, modVersion);
        if (result.isEmpty())
            return Optional.empty();

        var version = result.get().getFirst();
        return Optional.of(new VersionResult(version,displayName, info.downloadLocation));
    }

    public static Optional<VersionResult> getUpdateText(String displayName, Version minecraftVersion, Version modVersion, URL dataLocation) {
        var info = getVersionInformation(dataLocation);
        if (info.isEmpty())
            return Optional.empty();
        return getUpdateText(displayName, minecraftVersion, modVersion, info.get());
    }

    public static class VersionResult {
        public final Version version;
        public final String displayName;
        public final String downloadLocation;

        VersionResult(Version version, String displayName, String downloadLocation) {
            this.version = version;
            this.displayName = displayName;
            this.downloadLocation = downloadLocation;
        }

        public Text getChatText() {
            var formattedText = I18n.translate("dsurround.text.NewVersion", this.displayName, this.version.getFriendlyString(), this.downloadLocation);
            return Text.Serialization.fromJson(formattedText);
        }
    }
}
