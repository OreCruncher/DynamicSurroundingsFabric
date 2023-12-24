package org.orecruncher.dsurround.lib.resources;

import net.minecraft.util.Identifier;

public class ResourceAccessorBytes extends ResourceAccessorBase {

    public ResourceAccessorBytes(Identifier location, byte[] asset) {
        super(location, asset);
    }

    @Override
    protected byte[] getAsset() {
        throw new RuntimeException("Shouldn't get here?");
    }
}
