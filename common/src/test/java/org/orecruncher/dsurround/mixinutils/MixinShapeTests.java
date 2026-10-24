package org.orecruncher.dsurround.mixinutils;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against the mixins drifting back to the fragile forms they were moved away from.
 */
public class MixinShapeTests {

    @Test
    void soundPlayHookTakesOnlyTheChannelHandle() throws ClassNotFoundException {
        // Capturing all of play()'s locals broke whenever any of them changed; only the channel handle is needed
        var mixin = Class.forName("org.orecruncher.dsurround.mixins.audio.MixinSoundEngine", false, getClass().getClassLoader());
        var hook = Arrays.stream(mixin.getDeclaredMethods())
                .filter(m -> m.getName().equals("dsurround$onSoundPlay"))
                .findFirst()
                .orElseThrow();
        // play() returns a PlayResult in 26.2, so the hook gets a CallbackInfoReturnable
        assertEquals(List.of(SoundInstance.class, CallbackInfoReturnable.class, ChannelAccess.ChannelHandle.class),
                List.of(hook.getParameterTypes()));
    }

    @Test
    void clothMixinDoesNotReplaceTheMethod() throws ClassNotFoundException {
        // An @Overwrite would declare Cloth's method itself; the mixin now adjusts what Cloth returns instead
        var mixin = Class.forName("org.orecruncher.dsurround.mixins.core.MixinClothAbstractConfigEntry", false, getClass().getClassLoader());
        assertTrue(Arrays.stream(mixin.getDeclaredMethods()).noneMatch(m -> m.getName().equals("getDisplayedFieldName")));
    }
}
