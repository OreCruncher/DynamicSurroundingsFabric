package org.orecruncher.dsurround.lib;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class GameUtils {
    private GameUtils() {

    }

    // Client methods
    @Nullable
    public static PlayerEntity getPlayer() {
        return getMC().player;
    }

    @Nullable
    public static ClientWorld getWorld() {
        return getMC().world;
    }

    public static MinecraftClient getMC() {
        return MinecraftClient.getInstance();
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

    public static boolean displayDebug() {
        return getGameSettings().debugEnabled;
    }

    public static SoundManager getSoundHander() {
        return getMC().getSoundManager();
    }

    public static boolean isInGame() {
        return getWorld() != null && getPlayer() != null;
    }

    public static boolean isThirdPersonView() {
        return !isFirstPersonView();
    }

    public static boolean isFirstPersonView() {
        return getGameSettings().getPerspective() == Perspective.FIRST_PERSON;
    }
}