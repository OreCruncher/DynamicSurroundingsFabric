package org.orecruncher.dsurround.lib;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.Optional;

public final class GameUtils {
    private GameUtils() {

    }

    // Client methods
    public static Optional<Player> getPlayer() {
        return Optional.ofNullable(getMC().player);
    }

    public static Optional<ClientLevel> getWorld() {
        return Optional.ofNullable(getMC().level);
    }

    public static Optional<RegistryAccess> getRegistryManager() {
        return getWorld().map(ClientLevel::registryAccess);
    }

    public static Optional<Screen> getCurrentScreen() {
        return Optional.ofNullable(getMC().screen);
    }

    public static void setScreen(Screen screen) {
        getMC().setScreen(screen);
    }

    public static ParticleEngine getParticleManager() {
        return getMC().particleEngine;
    }

    public static Options getGameSettings() {
        return getMC().options;
    }

    public static Font getTextRenderer() {
        return getMC().font;
    }

    public static StringSplitter getTextHandler() {
        return getTextRenderer().getSplitter();
    }

    public static SoundManager getSoundManager() {
        return getMC().getSoundManager();
    }

    public static ResourceManager getResourceManager() {
        return getMC().getResourceManager();
    }

    public static TextureManager getTextureManager() {
        return getMC().getTextureManager();
    }

    public static boolean isInGame() {
        return getWorld().isPresent() && getPlayer().isPresent();
    }

    public static boolean isPaused()
    {
        return getMC().isPaused();
    }

    public static boolean isSinglePlayer()
    {
        return getMC().isSingleplayer();
    }

    public static boolean isFirstPersonView() {
        return getGameSettings().getCameraType() == CameraType.FIRST_PERSON;
    }

    public static Minecraft getMC() {
        return Objects.requireNonNull(Minecraft.getInstance());
    }

    public static Optional<String> getServerBrand() {
        var connection = getMC().getConnection();
        if (connection != null)
            return Optional.ofNullable(connection.serverBrand());
        return Optional.empty();
    }

    public static MinecraftServerType getServerType() {
        return getServerBrand().map(MinecraftServerType::fromBrand).orElse(MinecraftServerType.VANILLA);
    }
}