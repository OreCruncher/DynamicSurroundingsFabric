package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;

import java.util.ArrayList;

public final class ParticleSheets {

    private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);

    public static final Identifier TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE = new Identifier(Constants.MOD_ID, "textures/particles/pixel_ripples.png");

    public static final ParticleTextureSheet RIPPLE_RENDER =
            new ParticleRenderType(TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE) {
                @Override
                protected Identifier getTexture() {
                    return CONFIG.waterRippleStyle.getTexture();
                }
            };

    public static void register() {

        var manager = GameUtils.getTextureManager().orElseThrow();
        manager.registerTexture(TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE, new ResourceTexture(TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE));

        var existingSheets = MixinParticleManager.getParticleTextureSheets();
        assert existingSheets != null;
        existingSheets = new ArrayList<>(existingSheets);
        existingSheets.add(RIPPLE_RENDER);
        MixinParticleManager.setParticleTextureSheets(existingSheets);
    }
}
