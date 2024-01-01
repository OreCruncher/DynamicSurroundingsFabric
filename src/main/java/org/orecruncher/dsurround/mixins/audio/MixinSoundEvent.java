package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Can't believe there isn't a toString() override on this sucker
 */
@Mixin(SoundEvent.class)
public class MixinSoundEvent {

    @Shadow
    @Final
    private ResourceLocation location;

    public String toString() {
        return String.format("SoundEvent[%s]", this.location.toString());
    }
}
