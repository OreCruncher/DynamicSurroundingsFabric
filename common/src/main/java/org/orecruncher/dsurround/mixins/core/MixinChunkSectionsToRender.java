package org.orecruncher.dsurround.mixins.core;

import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frost on windows without Fabulous graphics: in the main pass, the scene's depth copied before the translucent blocks,
 * then the frost drawn after them and the tripwire, tested against that copy, so water and stained glass behind a
 * window are under its frost and those in front of it don't hide it (see WindowFrostHandler). The main pass draws its
 * layer groups through here.
 */
@Mixin(ChunkSectionsToRender.class)
public class MixinChunkSectionsToRender {

    @Inject(method = "renderGroup", at = @At("HEAD"), require = 0)
    private void dsurround$frostDepthBeforeTranslucent(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.TRANSLUCENT)
            MixinHelpers.WINDOW_FROST_GUARD.run(() -> MixinHelpers.windowFrost().copyDepthBeforeTranslucent());
    }

    @Inject(method = "renderGroup", at = @At("RETURN"), require = 0)
    private void dsurround$frostAfterTranslucentTerrain(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.TRIPWIRE)
            MixinHelpers.WINDOW_FROST_GUARD.run(() -> MixinHelpers.windowFrost().renderAfterTranslucentTerrain());
    }
}
