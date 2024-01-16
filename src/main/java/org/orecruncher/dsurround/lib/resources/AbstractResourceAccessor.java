package org.orecruncher.dsurround.lib.resources;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.Singleton;

abstract class AbstractResourceAccessor implements IResourceAccessor {

    private final ResourceLocation location;
    private final Singleton<byte[]> bytes;

    public AbstractResourceAccessor(final ResourceLocation location, byte[] bytes) {
        this.location = location;
        this.bytes = new Singleton<>(bytes);
    }

    public AbstractResourceAccessor(final ResourceLocation location) {
        this.location = location;
        this.bytes = new Singleton<>(this::getAsset);
    }

    @Override
    public ResourceLocation location() {
        return this.location;
    }

    @Override
    public byte[] asBytes() {
        return this.bytes.get();
    }

    abstract protected byte[] getAsset();

    @Override
    public String toString() {
        return this.location.toString();
    }

}