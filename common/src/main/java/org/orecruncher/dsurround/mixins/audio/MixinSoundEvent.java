package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

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
    private Optional<Float> fixedRange;

    public String toString() {
        return "%s{range %f}".formatted(this.location.toString(), this.fixedRange.orElse(16F));
    }
}
