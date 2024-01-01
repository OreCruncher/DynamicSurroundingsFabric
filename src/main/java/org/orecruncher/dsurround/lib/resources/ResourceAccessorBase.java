package org.orecruncher.dsurround.lib.resources;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.Singleton;

abstract class ResourceAccessorBase implements IResourceAccessor {

    private final ResourceLocation location;
    private final Singleton<byte[]> bytes;

    public ResourceAccessorBase(final ResourceLocation location, byte[] bytes) {
        this.location = location;
        this.bytes = new Singleton<>(() -> bytes);
    }

    public ResourceAccessorBase(final ResourceLocation location) {
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