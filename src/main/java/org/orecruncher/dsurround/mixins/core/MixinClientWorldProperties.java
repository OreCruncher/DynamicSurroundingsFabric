package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevel.ClientLevelData.class)
public interface MixinClientWorldProperties {
    @Accessor("isFlat")
    boolean dsurround_isFlatWorld();
}
