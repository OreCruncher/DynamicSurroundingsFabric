// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.renderer.ShaderManager;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.ModShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderManager.class)
public class MixinShaderManager {

    /**
     * The game's shaders have reloaded (and vanilla's pipelines compiled with them): compile the mod's pipelines too,
     * so each effect knows whether it can be drawn. Not reached if vanilla's own shaders failed, in which case the
     * game reloads again with its default resources.
     */
    @Inject(method = "apply(Lcom/mojang/renderpearl/api/device/GpuDevice;Lnet/minecraft/client/renderer/ShaderManager$PendingResults;)V", at = @At("TAIL"))
    private void dsurround$compileModShaders(CallbackInfo ci) {
        ModShaders.SHADERS.forEach(ModShader::validate);
    }
}
