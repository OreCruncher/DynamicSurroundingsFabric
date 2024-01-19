package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;

import java.util.ArrayList;

public final class ParticleSheets {

    private static Configuration.BlockEffects CONFIG;

    public static final ResourceLocation TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE = new ResourceLocation(Constants.MOD_ID, "textures/particles/pixel_ripples.png");

    public static final net.minecraft.client.particle.ParticleRenderType RIPPLE_RENDER =
            new DsurroundParticleRenderType(TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE) {
                @Override
                protected ResourceLocation getTexture() {
                    return CONFIG.waterRippleStyle.getTexture();
                }
            };

    public static void register() {

        CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);
        var manager = GameUtils.getTextureManager();
        manager.register(TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE, new SimpleTexture(TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE));

        var existingSheets = MixinParticleManager.dsurround_getParticleTextureSheets();
        assert existingSheets != null;
        existingSheets = new ArrayList<>(existingSheets);
        existingSheets.add(RIPPLE_RENDER);
        MixinParticleManager.dsurround_setParticleTextureSheets(existingSheets);
    }
}
