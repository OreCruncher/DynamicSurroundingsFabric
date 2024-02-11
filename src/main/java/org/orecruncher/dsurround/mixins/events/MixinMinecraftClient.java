package org.orecruncher.dsurround.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftClient {

    @Unique
    private Abilities dsurround_cachedAbilities;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void dsurround_tickStart(CallbackInfo info) {
        ClientState.TICK_START.raise().onTickStart((Minecraft) (Object) this);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void dsurround_tickEnd(CallbackInfo info) {
        ClientState.TICK_END.raise().onTickEnd((Minecraft) (Object) this);
    }

    @Inject(method = "destroy()V", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", shift = At.Shift.AFTER, remap = false))
    private void dsurround_stopping(CallbackInfo ci) {
        ClientState.STOPPING.raise().onStopping((Minecraft) (Object) this);
    }

    @Inject(method = "run()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", shift = At.Shift.AFTER, ordinal = 0))
    private void dsurround_starting(CallbackInfo ci) {
        ClientState.STARTED.raise().onStart((Minecraft) (Object) this);
    }

    /**
     * Hooks getting player abilities when checking whether to play situational music or the standard
     * creative Minecraft music when the player is in creative mode and in a dimension other than the Nether.
     * Substitute a fake Abilities instance to cause Minecraft to think the player is not in creative mode.
     *
     * Situational music is for playing music at in The End after a boss fight, while submerged underwater, or
     * if a biome has a background sound configured.
     */
    @WrapOperation(method = "getSituationalMusic()Lnet/minecraft/sounds/Music;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"))
    private Abilities dsurround_instabuildCheck(LocalPlayer instance, Operation<Abilities> original) {
        if (MixinHelpers.soundOptions.playBiomeMusicWhileCreative) {
            if (this.dsurround_cachedAbilities == null) {
                this.dsurround_cachedAbilities = new Abilities();
            }
            return this.dsurround_cachedAbilities;
        }
        return original.call(instance);
    }
}