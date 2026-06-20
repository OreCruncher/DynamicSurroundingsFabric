package org.orecruncher.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.particles.DsurroundParticleSpriteSets;
import org.orecruncher.dsurround.effects.particles.DsurroundParticleTypes;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.McCompat;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Implements the Fabric specific binding to initialize the mod.
 */
public final class FabricMod implements ClientModInitializer {

    private Client client;

    /**
     * Keep the Fabric entrypoint constructor side-effect free.  Some launchers and
     * Mod Menu paths instantiate entrypoints while Fabric/Mixin is still settling;
     * doing DS bootstrap work here can make startup failures look like silent
     * launcher exits.
     */
    public FabricMod() {
    }

    @Override
    public void onInitializeClient() {
        try {
            this.client = new Client();
            this.client.construct();

            this.registerParticleSpriteSets();

            // Boot the mod.
            this.client.initializeClient();

            OverlayManager overlayManager = ContainerManager.resolve(OverlayManager.class);
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "overlay_manager"), (graphics, deltaTracker) -> {
                try {
                    if (!McCompat.optionsHideGui(Minecraft.getInstance().options)) {
                        overlayManager.render(graphics, deltaTracker);
                    }
                } catch (Throwable t) {
                    Library.LOGGER.error(t, "Dynamic Surroundings overlay render failed");
                }
            });

            writeBootstrapLog("Dynamic Surroundings r14 startup path completed.", null);
        } catch (Throwable t) {
            // Do not take down the entire client while this 26.2 port is being stabilized.
            // A plain file is written because Modrinth can close before latest.log/crash-reports
            // are shown in the UI.
            writeBootstrapLog("Dynamic Surroundings r14 disabled itself during client initialization.", t);
            try {
                Library.LOGGER.error(t, "Dynamic Surroundings disabled itself during client initialization");
            } catch (Throwable ignored) {
            }
        }
    }

    private void registerParticleSpriteSets() {
        // Dynamic Surroundings creates the ripple particles manually, but Minecraft only loads
        // custom particle sprites into ParticleEngine.spriteSets when a provider is registered.
        // These no-op providers exist solely to bind the dsurround particle JSON files to sprite sets.
        ParticleProviderRegistry.getInstance().register(DsurroundParticleTypes.WATER_RIPPLE,
                sprites -> new SpriteOnlyProvider(DsurroundParticleTypes.WATER_RIPPLE, sprites));
        ParticleProviderRegistry.getInstance().register(DsurroundParticleTypes.WATER_RIPPLE_PIXELATED,
                sprites -> new SpriteOnlyProvider(DsurroundParticleTypes.WATER_RIPPLE_PIXELATED, sprites));
    }

    private static void writeBootstrapLog(String message, Throwable error) {
        try {
            Path logFile = FabricLoader.getInstance().getGameDir().resolve("dsurround-r14-startup.log");
            StringBuilder sb = new StringBuilder(1024);
            sb.append('[').append(Instant.now()).append("] ").append(message).append(System.lineSeparator());
            if (error != null) {
                sb.append(error.getClass().getName()).append(": ").append(error.getMessage()).append(System.lineSeparator());
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }
            Files.writeString(logFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            if (error != null) {
                error.printStackTrace(System.err);
            }
        }
    }

    private static final class SpriteOnlyProvider implements ParticleProvider<SimpleParticleType> {
        private SpriteOnlyProvider(SimpleParticleType particleType, SpriteSet spriteSet) {
            DsurroundParticleSpriteSets.register(particleType, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            return null;
        }
    }

}
