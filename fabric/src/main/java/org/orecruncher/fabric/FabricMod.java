package org.orecruncher.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.particles.SoftParticles;

/**
 * Implements the Fabric specific binding to initialize the mod
 */
public final class FabricMod {

    public static void initialize() {
        Client.initialize();
    }

    public static void initializeClient() {
        Client.initializeClient();

        CoreShaderRegistrationCallback.EVENT.register(context -> {
            // A shader that fails to compile throws here. Caught so the game still loads, drawing particles without it.
            try {
                context.register(SoftParticles.SHADER_ID, DefaultVertexFormat.PARTICLE, SoftParticles::onShaderLoaded);
            } catch (Exception e) {
                SoftParticles.onShaderFailed(e);
            }
        });
    }
}
