package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The frustum the level renderer culls with this frame, at the camera, for things drawn during world rendering
 * that cull themselves (the light level overlay)
 */
@Mixin(LevelRenderer.class)
public interface MixinLevelRendererAccessor {

    @Accessor("cullingFrustum")
    Frustum dsurround$getCullingFrustum();
}
