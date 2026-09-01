package org.orecruncher.dsurround.effects.particles;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.ArrayList;

public final class ParticleUtils {

    private static final IRandomizer RANDOM = Randomizer.current();

    public static SpriteSet getSpriteProvider(ParticleType<?> particleType) {
        var id = RegistryUtils.getRegistry(Registries.PARTICLE_TYPE)
                .map(r -> r.getResourceKey(particleType).map(ResourceKey::location))
                .orElseThrow();
        return GameUtils.getParticleManager().spriteSets.get(id.get());
    }

    public static Vec3 getBreathOrigin(final LivingEntity entity) {
        final Vec3 eyePosition = eyePosition(entity).subtract(0D, entity.isBaby() ? 0.1D : 0.2D, 0D);
        final Vec3 look = entity.getViewVector(1F); // Don't use the other look vector method!
        return eyePosition.add(look.scale(entity.isBaby() ? 0.25D : 0.5D));
    }

    public static Vec3 getLookTrajectory(final LivingEntity entity) {
        return entity.getLookAngle()
                .zRot(RANDOM.nextFloat() * 2F)   // yaw
                .yRot(RANDOM.nextFloat() * 2F)   // pitch
                .normalize();
    }

    /*
     * Use some corrective lenses because the MC routine just doesn't lower the
     * height enough for our rendering purpose.
     */
    private static Vec3 eyePosition(final Entity e) {
        var y = e.getEyePosition();
        if (e.isCrouching()) {
            y = y.subtract(0, 0.25D, 0);
        }
        return y;
    }

    /*
     * Custom particle rendering. It is expected that each particle collection that renders
     * will bind the appropriate texture sheet rather than using the shared Minecraft sheet.
     */
    public static final ParticleRenderType DSURROUND_CUSTOM = new ParticleRenderType() {
        public BufferBuilder begin(Tesselator tesselator, @NotNull TextureManager textureManager) {
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "DSURROUND_CUSTOM_PARTICLE";
        }
    };

    public static void register() {
        // Add our custom particle render to the render order list in the ParticleEngine. We insert right before
        // CUSTOM.
        ArrayList<ParticleRenderType> newList = new ArrayList<>();
        for (var entry : ParticleEngine.RENDER_ORDER) {
            if (entry == ParticleRenderType.CUSTOM) {
                newList.add(DSURROUND_CUSTOM);
            }
            newList.add(entry);
        }
        ParticleEngine.RENDER_ORDER = ImmutableList.copyOf(newList);
    }
}