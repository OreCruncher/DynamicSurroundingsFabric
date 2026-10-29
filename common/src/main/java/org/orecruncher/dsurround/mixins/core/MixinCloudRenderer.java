package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class MixinCloudRenderer {

    /**
     * The mod's clouds in place of vanilla's: drawn where vanilla's would be, in the frame's clouds pass, and vanilla's
     * left out. When ours can't be drawn, vanilla's are, as they would have been. On the renderer itself rather than
     * where the level calls it, which is a lambda in the frame's clouds pass.
     */
    @Inject(method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$clouds(int color, CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition, long gameTime,
                                  float partialTicks, CallbackInfo ci) {
        if (MixinHelpers.CLOUDS_GUARD.get(() -> MixinHelpers.clouds().render(color, cameraPosition, partialTicks), false))
            ci.cancel();
    }
}
