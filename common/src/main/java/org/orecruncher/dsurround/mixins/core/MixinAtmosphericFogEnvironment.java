package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.orecruncher.dsurround.eventing.IFogRender;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtmosphericFogEnvironment.class)
public class MixinAtmosphericFogEnvironment {

    @Inject(method = "setupFog(Lnet/minecraft/client/renderer/fog/FogData;Lnet/minecraft/client/Camera;Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void dsurround$renderFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
        IFogRender.EVENT.invoker().onRenderFog(fog, renderDistance, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    /**
     * The storm's strength in place of the world's rain level for the game's own rain fog, which closes in out in the
     * open (the mod's weather fog adds only a dust storm's): a light storm thickens it less than a downpour.
     */
    @ModifyExpressionValue(method = "updateRainFogState(Lnet/minecraft/client/Camera;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"), require = 0)
    private float dsurround$stormRainFog(float vanillaLevel) {
        return MixinHelpers.stormRainLevel(vanillaLevel);
    }
}
