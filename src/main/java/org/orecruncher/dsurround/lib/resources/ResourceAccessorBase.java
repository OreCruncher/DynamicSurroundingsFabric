package org.orecruncher.dsurround.lib.resources;

import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.lib.Singleton;

abstract class ResourceAccessorBase implements IResourceAccessor {

    private final Identifier location;
    private final Singleton<byte[]> bytes;

    public ResourceAccessorBase(final Identifier location, byte[] bytes) {
        this.location = location;
        this.bytes = new Singleton<>(() -> bytes);
    }

    public ResourceAccessorBase(final Identifier location) {
        this.location = location;
        this.bytes = new Singleton<>(this::getAsset);
    }

    @Override
    public Identifier location() {
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