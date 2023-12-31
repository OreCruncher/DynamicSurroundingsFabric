package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.world.ClientChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientChunkManager.class)
public interface MixinClientChunkManager {

    @Accessor("chunks")
    ClientChunkManager.ClientChunkMap dsurround_getClientChunkMap();
}
