package org.orecruncher.dsurround.lib.resources;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ResourceAccessorBytes extends ResourceAccessorBase {

    public ResourceAccessorBytes(Identifier location, byte[] asset) {
        super(location, asset);
    }

    @Override
    protected byte[] getAsset() {
        throw new RuntimeException("Shouldn't get here?");
    }
}
