package org.orecruncher.dsurround.lib.resources;

import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;

final class ResourceAccessorJar extends ResourceAccessorBase {

    // Used to find assets within the current jar
    final String asset;

    public ResourceAccessorJar(final String rootContainer, final Identifier location) {
        this(location, String.format("/assets/%s/%s/%s", rootContainer, location.getNamespace(), location.getPath()));
    }

    public ResourceAccessorJar(final Identifier location, final String asset) {
        super(location);
        this.asset = asset;
    }

    @Override
    protected byte[] getAsset() {
        try (InputStream stream = ResourceAccessorJar.class.getResourceAsStream(this.asset)) {
            // Will be null if resource not found
            if (stream != null)
                return IOUtils.toByteArray(stream);
        } catch (final Throwable t) {
            logError(t);
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", super.toString(), this.asset);
    }
}