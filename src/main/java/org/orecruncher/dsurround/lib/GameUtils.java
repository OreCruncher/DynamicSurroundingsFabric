package org.orecruncher.dsurround.lib;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.registry.*;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.Optional;

/*
 * NOTE: MinecraftClient is an AutoClosable that gives IDEs a bit of a fit with warnings.  The mods usage
 * context does not require closing, so it is safe to ignore.
 */
public final class GameUtils {
    private GameUtils() {

    }

    // Client methods
    public static Optional<PlayerEntity> getPlayer() {
        return Optional.ofNullable(getMC().player);
    }

    public static Optional<ClientWorld> getWorld() {
        return Optional.ofNullable(getMC().world);
    }

    public static Optional<DynamicRegistryManager> getRegistryManager() {
        return getWorld().map(World::getRegistryManager);
    }

    public static Optional<Screen> getCurrentScreen() {
        return Optional.ofNullable(getMC().currentScreen);
    }

    public static void setScreen(Screen screen)
    {
        getMC().setScreen(screen);
    }

    public static Optional<ParticleManager> getParticleManager() {
        return Optional.ofNullable(getMC().particleManager);
    }

    public static Optional<GameOptions> getGameSettings() {
        return Optional.ofNullable(getMC().options);
    }

    public static Optional<TextRenderer> getTextRenderer() {
        return Optional.ofNullable(getMC().textRenderer);
    }

    public static Optional<TextHandler> getTextHandler() {
        return getTextRenderer().map(TextRenderer::getTextHandler);
    }

    public static Optional<SoundManager> getSoundManager() {
        return Optional.ofNullable(getMC().getSoundManager());
    }

    public static Optional<ResourceManager> getResourceManager() {
        return Optional.ofNullable(getMC().getResourceManager());
    }

    public static Optional<TextureManager> getTextureManager() {
        return Optional.ofNullable(getMC().getTextureManager());
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
        return getMC().isInSingleplayer();
    }

    public static boolean isFirstPersonView() {
        var settings = getGameSettings();
        return settings.map(s -> s.getPerspective() == Perspective.FIRST_PERSON).orElse(true);
    }

    public static MinecraftClient getMC() {
        return Objects.requireNonNull(MinecraftClient.getInstance());
    }
}