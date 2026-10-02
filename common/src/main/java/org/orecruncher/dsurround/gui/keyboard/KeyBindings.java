package org.orecruncher.dsurround.gui.keyboard;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.eventing.IClientTickEnd;
import org.orecruncher.dsurround.gui.overlay.DiagnosticsOverlay;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.IdentityHashMap;
import java.util.Map;

public final class KeyBindings {

    private static final Map<KeyMapping, Runnable> keyPressHandlers = new IdentityHashMap<>();

    public static void register() {
        var modMenuKey = Platform.isModLoaded(Constants.MODMENU) ? InputConstants.UNKNOWN.getValue() : InputConstants.KEY_EQUALS;

        registerKeyBinding(
                "modConfigurationMenu",
                modMenuKey,
                () -> ContainerManager.resolve(IConfigScreenFactoryProvider.class)
                        .getModConfigScreenFactory(Configuration.class)
                        .ifPresentOrElse(
                                f -> f.create(null),
                                () -> Library.LOGGER.info("Configuration GUI libraries not present")
                        )
        );

        registerKeyBinding(
                "individualSoundConfig",
                InputConstants.UNKNOWN.getValue(),
                // The screen handles stopping sounds and pausing the music itself
                () -> GameUtils.setScreen(new IndividualSoundControlScreen(null, GameUtils.isSinglePlayer()))
        );

        registerKeyBinding(
                "diagnosticHud",
                InputConstants.UNKNOWN.getValue(),
                () -> ContainerManager.resolve(DiagnosticsOverlay.class).toggleCollection()
        );

        IClientTickEnd.EVENT.register(KeyBindings::handleMenuKeyPress);
    }

    private static void registerKeyBinding(String translationKey, int code, Runnable handler) {
        var mapping = new KeyMapping("dsurround.text.keybind." + translationKey, code, "dsurround.text.keybind.section");
        KeyMappingRegistry.register(mapping);
        keyPressHandlers.put(mapping, handler);
    }

    private static void handleMenuKeyPress(Minecraft client) {
        if (GameUtils.getCurrentScreen().isPresent() || GameUtils.getPlayer().isEmpty())
            return;

        for (var kvp : keyPressHandlers.entrySet())
            if (kvp.getKey().consumeClick())
                kvp.getValue().run();
    }
}
