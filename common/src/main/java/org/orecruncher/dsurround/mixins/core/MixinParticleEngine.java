package org.orecruncher.dsurround.mixins.core;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleOptions;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleRenderType;
import org.orecruncher.dsurround.effects.particles.ParticleBatches;
import org.orecruncher.dsurround.mixinutils.IWindBlown;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.weather.Shelter;
import org.orecruncher.dsurround.tags.ParticleTags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Shadow
    @Final
    @Mutable
    private static List<ParticleRenderType> RENDER_ORDER;

    @Shadow
    protected ClientLevel level;

    @Inject(method="<clinit>", at = @At("TAIL"))
    private static void dsurround$registerRenderTypes(CallbackInfo ci) {

        var listBuilder = ImmutableList.<ParticleRenderType>builder();

        for( var entry : RENDER_ORDER ) {
            if (entry == ParticleRenderType.CUSTOM) {
                // Insert our custom types before CUSTOM. By default, CUSTOM is at the end of the render
                // ordering list.
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_MIST);
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_FIREFLY);
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_FLAME);
                listBuilder.add(DSurroundParticleRenderType.PARTICLE_SHEET_EYE_FLAME);
            }
            listBuilder.add(entry);
        }

        RENDER_ORDER = listBuilder.build();
    }

    /**
     * Marks the particles the wind carries, as they are made: this is the only place a particle's type is known.
     * Particles handed straight to {@code add} (the mod's own, block breaking) have none, so are never marked. Nor are
     * particles another mod makes off the client thread: Shelter looks at the world with scratch state of its own,
     * client thread only.
     */
    @Inject(method = "makeParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"), require = 0)
    private void dsurround$markWindBlown(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
        var particle = cir.getReturnValue();
        if (particle == null || !MixinHelpers.particleTweaksConfig.windBlowsParticles || !Minecraft.getInstance().isSameThread())
            return;
        try {
            if (!MixinHelpers.TAG_LIBRARY.is(ParticleTags.WIND_BLOWN, options.getType()))
                return;
            // Sheltered where it was made: indoors or in a cave, it stays as it is
            var exposure = Shelter.exposure(this.level, BlockPos.containing(x, y, z));
            if (exposure > 0F)
                ((IWindBlown) particle).dsurround$blowInWind(exposure);
        } catch (Throwable t) {
            MixinHelpers.WIND_BLOWN_GUARD.failed(t);
        }
    }

    /**
     * Puts the usual blend back once the particles are drawn: flames are drawn adding their light, and a render type
     * can't undo that itself. Whatever is drawn next and turns blending on without setting how (vanilla's rain, for
     * one) would otherwise add too.
     */
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", at = @At("TAIL"))
    private void dsurround$restoreBlend(CallbackInfo ci) {
        RenderSystem.defaultBlendFunc();
    }

    /**
     * The same, for NeoForge, whose level renderer draws particles through a render method of its own. Vanilla has no
     * such method, so it isn't remapped (NeoForge runs on these names) and isn't required (Fabric hasn't got it):
     * otherwise the common module's build warns it can't be found.
     */
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void dsurround$restoreBlendNeoForge(CallbackInfo ci) {
        RenderSystem.defaultBlendFunc();
    }

    // ---- Keeping each batch drawing with its render type's state (see ParticleBatches) --------------------------
    // Each step has a handler for Fabric's render method and one for NeoForge's, as for the blend above.

    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleRenderType;begin(Lcom/mojang/blaze3d/vertex/Tesselator;Lnet/minecraft/client/renderer/texture/TextureManager;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"),
            require = 0)
    private BufferBuilder dsurround$batchBegun(ParticleRenderType type, Tesselator tesselator, TextureManager textures, Operation<BufferBuilder> original) {
        var builder = original.call(type, tesselator, textures);
        dsurround$begun(type, builder);
        return builder;
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleRenderType;begin(Lcom/mojang/blaze3d/vertex/Tesselator;Lnet/minecraft/client/renderer/texture/TextureManager;)Lcom/mojang/blaze3d/vertex/BufferBuilder;", remap = false),
            remap = false, require = 0)
    private BufferBuilder dsurround$batchBegunNeoForge(ParticleRenderType type, Tesselator tesselator, TextureManager textures, Operation<BufferBuilder> original) {
        var builder = original.call(type, tesselator, textures);
        dsurround$begun(type, builder);
        return builder;
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"),
            require = 0)
    private void dsurround$particleRendered(Particle particle, VertexConsumer consumer, Camera camera, float partialTick, Operation<Void> original) {
        dsurround$render(particle, consumer, camera, partialTick, original);
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", remap = false),
            remap = false, require = 0)
    private void dsurround$particleRenderedNeoForge(Particle particle, VertexConsumer consumer, Camera camera, float partialTick, Operation<Void> original) {
        dsurround$render(particle, consumer, camera, partialTick, original);
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"),
            require = 0)
    private void dsurround$batchDrawn(MeshData mesh, Operation<Void> original) {
        dsurround$beforeDraw();
        original.call(mesh);
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V", remap = false),
            remap = false, require = 0)
    private void dsurround$batchDrawnNeoForge(MeshData mesh, Operation<Void> original) {
        dsurround$beforeDraw();
        original.call(mesh);
    }

    @Unique
    private static void dsurround$begun(ParticleRenderType type, BufferBuilder builder) {
        try {
            ParticleBatches.begun(type, builder);
        } catch (Throwable t) {
            MixinHelpers.PARTICLE_BATCH_GUARD.failed(t);
        }
    }

    /**
     * Renders a particle into the batch, or, if it is one found changing the batch's state, on its own. A particle that
     * throws while rendering is left to the game's crash handling, as it would be without this.
     */
    @Unique
    private static void dsurround$render(Particle particle, VertexConsumer consumer, Camera camera, float partialTick, Operation<Void> original) {
        boolean isolate;
        try {
            isolate = ParticleBatches.isolates(particle);
        } catch (Throwable t) {
            MixinHelpers.PARTICLE_BATCH_GUARD.failed(t);
            isolate = false;
        }
        if (isolate) {
            ParticleBatches.isolated(own -> original.call(particle, own, camera, partialTick));
            return;
        }
        original.call(particle, consumer, camera, partialTick);
        try {
            ParticleBatches.rendered(particle);
        } catch (Throwable t) {
            MixinHelpers.PARTICLE_BATCH_GUARD.failed(t);
        }
    }

    @Unique
    private static void dsurround$beforeDraw() {
        try {
            ParticleBatches.beforeDraw();
        } catch (Throwable t) {
            MixinHelpers.PARTICLE_BATCH_GUARD.failed(t);
        }
    }
}
