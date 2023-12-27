package org.orecruncher.dsurround.lib.version;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public class VersionData {

    public static final Codec<SemanticVersion> CODEC = Codec.STRING
            .comapFlatMap(
                    VersionData::manifest,
                    SemanticVersion::toString).stable();

    private static DataResult<SemanticVersion> manifest(String versionData) {
        try {
            return DataResult.success(new SemanticVersion(versionData));
        } catch (Throwable t) {
            return DataResult.error(t::getMessage);
        }
    }
}
