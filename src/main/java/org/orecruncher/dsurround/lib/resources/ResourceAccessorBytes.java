package org.orecruncher.dsurround.lib.resources;

import net.minecraft.resources.ResourceLocation;

public class ResourceAccessorBytes extends ResourceAccessorBase {

    public ResourceAccessorBytes(ResourceLocation location, byte[] asset) {
        super(location, asset);
    }

    @Override
    protected byte[] getAsset() {
        throw new RuntimeException("Shouldn't get here?");
    }
}
