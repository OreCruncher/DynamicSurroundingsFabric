package org.orecruncher.dsurround.mixins.events;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.eventing.ClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftClient {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void dsurround_tickStart(CallbackInfo info) {
        ClientState.TICK_START.raise().onTickStart((Minecraft) (Object) this);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void dsurround_tickEnd(CallbackInfo info) {
        ClientState.TICK_END.raise().onTickEnd((Minecraft) (Object) this);
    }

    @Inject(method = "destroy()V", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", shift = At.Shift.AFTER), remap = false)
    private void dsurround_stopping(CallbackInfo ci) {
        ClientState.STOPPING.raise().onStopping((Minecraft) (Object) this);
    }

    @Inject(method = "run()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", shift = At.Shift.AFTER, ordinal = 0))
    private void dsurround_starting(CallbackInfo ci) {
        ClientState.STARTED.raise().onStart((Minecraft) (Object) this);
    }
}