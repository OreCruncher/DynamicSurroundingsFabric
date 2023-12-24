package org.orecruncher.dsurround.lib.version;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.loader.api.Version;

public class VersionData {

    public static final Codec<Version> CODEC = Codec.STRING
            .comapFlatMap(
                    VersionData::manifest,
                    Version::getFriendlyString).stable();

    private static DataResult<Version> manifest(String versionData) {
        try {
            return DataResult.success(Version.parse(versionData));
        } catch (Throwable t) {
            return DataResult.error(t::getMessage);
        }
    }
}
