package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
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
    @Inject(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void dsurround$compileModShaders(ShaderManager.Configs preparations, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        ModShaders.SHADERS.forEach(ModShader::validate);
    }
}
