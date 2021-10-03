package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import org.orecruncher.dsurround.Client;

/**
 * Handler that gets ticked by client ticks.  When the overlay to the main screen fades
 * it will play a startup sound if it hasn't done so already.
 */
@Environment(EnvType.CLIENT)
public final class StartupSoundHandler {

    private static boolean startupSoundPlayed = false;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(StartupSoundHandler::doStartupSound);
    }

    public static void doStartupSound(final MinecraftClient client) {
        if (startupSoundPlayed || client.getOverlay() != null)
            return;

        startupSoundPlayed = true;
        Client.SoundConfig
                .getRandomStartupSound()
                .ifPresent(id -> client.getSoundManager().play(PositionedSoundInstance.ambient(id)));
    }
}
