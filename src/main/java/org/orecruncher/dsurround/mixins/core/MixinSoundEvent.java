package org.orecruncher.dsurround.mixins.core;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
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
    private Identifier id;

    public String toString() {
        return String.format("SoundEvent[%s]", id.toString());
    }
}
