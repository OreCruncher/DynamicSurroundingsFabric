package org.orecruncher.dsurround.lib.resources;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;

@Environment(EnvType.CLIENT)
final class ResourceAccessorPack extends ResourceAccessorBase {

    private final ResourcePack pack;
    private final Identifier actual;

    public ResourceAccessorPack(final Identifier location, final ResourcePack pack, final Identifier actual) {
        super(location);
        this.pack = pack;
        this.actual = actual;
    }

    @Override
    protected byte[] getAsset() {
        try {
            try (InputStream stream = this.pack.open(ResourceType.CLIENT_RESOURCES, this.actual)) {
                return IOUtils.toByteArray(stream);
            }
        } catch (final Throwable t) {
            logError(t);
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s (%s = %s)", super.toString(), this.pack.getName(), this.actual.toString());
    }
}