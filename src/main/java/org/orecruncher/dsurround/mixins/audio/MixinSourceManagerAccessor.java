package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChannelAccess.ChannelHandle.class)
public interface MixinSourceManagerAccessor {

    @Accessor("channel")
    Channel dsurround_getSource();
}
