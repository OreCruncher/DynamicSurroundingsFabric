package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.SourceManager.class)
public interface MixinSourceManagerAccessor {

    @Accessor("source")
    Source dsurround_getSource();
}
