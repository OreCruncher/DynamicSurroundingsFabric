package org.orecruncher.dsurround.lib.version;

import net.minecraft.ChatFormatting;
import org.orecruncher.dsurround.lib.codec.CodecExtensions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.orecruncher.dsurround.lib.platform.ModInformation;

public class VersionChecker implements IVersionChecker {

    private final ModInformation modInfo;

    public VersionChecker(ModInformation modInformation) {
        this.modInfo = modInformation;
    }

    @Override
    public Optional<VersionResult> getVersionResult() {
        var url = this.modInfo.getUpdateUrl()
                .orElseThrow(() -> new VersionCheckException("there is no update URL"));
        var minecraftVersion = ModInformation.getMinecraftVersion()
                .orElseThrow(() -> new VersionCheckException("the Minecraft version can't be compared"));
        var info = CodecExtensions.deserialize(url.toString(), fetch(url), VersionInformation.CODEC)
                .orElseThrow(() -> new VersionCheckException("the version information from " + url + " couldn't be read"));

        return info.getRecommendation(minecraftVersion).map(recommendation -> {
            var version = recommendation.version();
            var updateAvailable = this.modInfo.version().compareTo(version) < 0;
            return new VersionResult(version.toString(), this.modInfo.modId(), ChatFormatting.stripFormatting(this.modInfo.displayName()), this.modInfo.curseForgeLink(), this.modInfo.modrinthLink(), recommendation.releaseNotesUrl(), this.modInfo.discussionsLink(), updateAvailable);
        });
    }

    private static String fetch(URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new VersionCheckException("unable to fetch " + url + " (" + e + ")", e);
        }
    }
}
