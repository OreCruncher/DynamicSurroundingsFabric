package org.orecruncher.dsurround.lib;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Keyboard;
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
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.registry.*;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/*
 * NOTE:  MinecraftClient is an AutoClosable that gives IDEs a bit of a fit with warnings.  The mods usage
 * context does not require closing so it is safe to ignore.
 */
@Environment(EnvType.CLIENT)
public final class GameUtils {
    private GameUtils() {

    }

    // Client methods
    @Nullable
    public static PlayerEntity getPlayer() {
        return getMC().player;
    }

    public static ClientWorld getWorld() {
        return Objects.requireNonNull(getMC().world);
    }

    public static DynamicRegistryManager getRegistryManager() {
        return getWorld().getRegistryManager();
    }

    public static Screen getCurrentScreen()
    {
        return getMC().currentScreen;
    }

    public static void setScreen(Screen screen)
    {
        getMC().setScreen(screen);
    }

    public static ParticleManager getParticleManager()
    {
        return getMC().particleManager;
    }

    public static Keyboard getKeyboard()
    {
        return getMC().keyboard;
    }

    public static GameOptions getGameSettings() {
        return getMC().options;
    }

    public static TextRenderer getTextRenderer() {
        return getMC().textRenderer;
    }

    public static TextHandler getTextHandler() {
        return getTextRenderer().getTextHandler();
    }

    public static SoundManager getSoundManager() {
        return getMC().getSoundManager();
    }

    public static ResourcePackManager getResourcePackManager() {
        return getMC().getResourcePackManager();
    }

    public static ResourceManager getResourceManager() {
        return getMC().getResourceManager();
    }

    public static TextureManager getTextureManager()
    {
        return getMC().getTextureManager();
    }

    public static boolean isInGame() {
        return getWorld() != null && getPlayer() != null;
    }

    public static boolean isPaused()
    {
        return getMC().isPaused();
    }

    public static boolean isSinglePlayer()
    {
        return getMC().isInSingleplayer();
    }

    public static boolean isThirdPersonView() {
        return !isFirstPersonView();
    }

    public static boolean isFirstPersonView() {
        return getGameSettings().getPerspective() == Perspective.FIRST_PERSON;
    }

    public static <T> Stream<Pair<T, Stream<TagKey<T>>>> getTagGroup(RegistryKey<? extends Registry<T>> registryKey) {
        return GameUtils.getWorld().getRegistryManager().get(registryKey).streamEntries()
                .map(reference -> Pair.of(reference.value(), reference.streamTags()));
    }

    public static MinecraftClient getMC() {
        return Objects.requireNonNull(MinecraftClient.getInstance());
    }
}