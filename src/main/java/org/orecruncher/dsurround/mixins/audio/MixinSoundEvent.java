package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Can't believe there isn't a toString() override
 */
@Mixin(SoundEvent.class)
public class MixinSoundEvent {

    @Shadow
    @Final
    private ResourceLocation location;
    @Shadow
    @Final
    private float range;
    @Shadow
    @Final
    private boolean newSystem;

    public String toString() {
        return "%s{newSystem %s, range %f}".formatted(this.location.toString(), Boolean.toString(this.newSystem), this.range);
    }
}
