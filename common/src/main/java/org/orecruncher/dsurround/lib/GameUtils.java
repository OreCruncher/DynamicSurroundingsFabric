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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        var mc = getMC();
        return getGuiController(mc)
                .flatMap(GameUtils::readCurrentScreenFrom)
                .or(() -> readCurrentScreenFrom(mc));
    }

    public static void setScreen(Screen screen) {
        var mc = getMC();
        if (getGuiController(mc).map(gui -> invokeSetScreen(gui, screen)).orElse(false)) {
            return;
        }

        if (invokeSetScreen(mc, screen)) {
            return;
        }

        throw new IllegalStateException("Unable to set the active Minecraft screen");
    }

    private static Optional<Object> getGuiController(Minecraft mc) {
        return readNamedMember(mc, "gui");
    }

    private static Optional<Screen> readCurrentScreenFrom(Object instance) {
        for (var memberName : new String[] { "screen", "currentScreen", "getScreen", "getCurrentScreen" }) {
            var value = readNamedMember(instance, memberName);
            if (value.isPresent()) {
                var candidate = value.get();
                if (candidate instanceof Screen screen) {
                    return Optional.of(screen);
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<Object> readNamedMember(Object instance, String name) {
        var field = findField(instance.getClass(), name);
        if (field != null) {
            try {
                return Optional.ofNullable(field.get(instance));
            } catch (IllegalAccessException ignored) {
                // Try method access below before giving up.
            }
        }

        var method = findMethod(instance.getClass(), name);
        if (method != null && method.getParameterCount() == 0) {
            try {
                return Optional.ofNullable(method.invoke(instance));
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static boolean invokeSetScreen(Object target, Screen screen) {
        if (invokeScreenMethod(target, "setScreen", screen)) {
            return true;
        }

        // 26.x exposes this helper in some loader/mapping combinations. It expects
        // a non-null screen, so keep it behind the old nullable setScreen attempt.
        if (screen != null && invokeScreenMethod(target, "setScreenAndShow", screen)) {
            return true;
        }

        // Last resort for no-remap snapshots where the normal setter is not exposed
        // to the compile classpath. This keeps Mod Menu/config navigation usable.
        return writeScreenField(target, screen);
    }

    private static boolean invokeScreenMethod(Object target, String name, Screen screen) {
        var method = findMethod(target.getClass(), name, Screen.class);
        if (method == null) {
            return false;
        }

        try {
            method.invoke(target, screen);
            return true;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Unable to set the active Minecraft screen", ex);
        }
    }

    private static boolean writeScreenField(Object target, Screen screen) {
        for (var fieldName : new String[] { "screen", "currentScreen" }) {
            var field = findField(target.getClass(), fieldName);
            if (field == null || !Screen.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                var previous = field.get(target);
                if (previous instanceof Screen previousScreen && previousScreen != screen) {
                    previousScreen.removed();
                }

                field.set(target, screen);
                if (screen != null) {
                    var window = getMC().getWindow();
                    screen.init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
                    screen.added();
                }
                return true;
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Unable to set the active Minecraft screen", ex);
            }
        }

        return false;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                var method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Keep walking the type hierarchy.
            }
        }

        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                var field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Keep walking the type hierarchy.
            }
        }

        return null;
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
        return McCompat.isSinglePlayer(getMC());
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