package org.orecruncher.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.ModShaders;

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
            // A shader that fails to compile throws here. Caught so the game still loads, without that shader.
            for (var shader : ModShaders.SHADERS) {
                try {
                    context.register(shader.id(), shader.format(), shader.onLoaded()::accept);
                } catch (Exception e) {
                    shader.onFailed().accept(e);
                }
            }
        });
    }
}
