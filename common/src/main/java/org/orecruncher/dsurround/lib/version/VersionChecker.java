package org.orecruncher.dsurround.lib.version;

import net.minecraft.ChatFormatting;
import org.orecruncher.dsurround.lib.codec.CodecExtensions;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.orecruncher.dsurround.lib.platform.ModInformation;

public class VersionChecker implements IVersionChecker {

    /**
     * The most the version information may be. It is a few kilobytes; anything near this isn't what was expected.
     */
    static final int MAX_BYTES = 128 * 1024;

    /**
     * Version information at least this big is logged, so it can be trimmed before it reaches {@link #MAX_BYTES}
     */
    static final int WARN_BYTES = MAX_BYTES * 4 / 5;

    private final ModInformation modInfo;
    private final IModLog logger;

    public VersionChecker(ModInformation modInformation, IModLog logger) {
        this.modInfo = modInformation;
        this.logger = logger;
    }

    @Override
    public Optional<VersionResult> getVersionResult() {
        var url = this.modInfo.getUpdateUrl()
                .orElseThrow(() -> new VersionCheckException("there is no update URL"));
        var minecraftVersion = ModInformation.getMinecraftVersion()
                .orElseThrow(() -> new VersionCheckException("the Minecraft version can't be compared"));
        var info = CodecExtensions.deserialize(url.toString(), this.fetch(url), VersionInformation.CODEC)
                .orElseThrow(() -> new VersionCheckException("the version information from " + url + " couldn't be read"));

        return info.getRecommendation(minecraftVersion).map(recommendation -> {
            var version = recommendation.version();
            var updateAvailable = this.modInfo.version().compareTo(version) < 0;
            return new VersionResult(version.toString(), this.modInfo.modId(), ChatFormatting.stripFormatting(this.modInfo.displayName()), this.modInfo.curseForgeLink(), this.modInfo.modrinthLink(), recommendation.releaseNotesUrl(), this.modInfo.discussionsLink(), updateAvailable);
        });
    }

    private String fetch(URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            try (InputStream in = connection.getInputStream()) {
                return read(url.toString(), in, this.logger);
            }
        } catch (IOException e) {
            throw new VersionCheckException("unable to fetch " + url + " (" + e + ")", e);
        }
    }

    /**
     * Reads the version information from {@code in}, refusing more than {@link #MAX_BYTES} and logging a warning
     * from {@link #WARN_BYTES}.
     *
     * @param source where it comes from, for the messages
     */
    static String read(String source, InputStream in, IModLog logger) throws IOException {
        // One byte past the cap tells too big from exactly the cap
        var data = in.readNBytes(MAX_BYTES + 1);
        if (data.length > MAX_BYTES)
            throw new VersionCheckException("the version information from " + source + " is more than " + MAX_BYTES + " bytes");
        if (data.length >= WARN_BYTES)
            logger.warn("The version information from %s is %d bytes, near the limit of %d", source, data.length, MAX_BYTES);
        return new String(data, StandardCharsets.UTF_8);
    }
}
