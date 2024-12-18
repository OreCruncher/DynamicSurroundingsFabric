package org.orecruncher.dsurround.gui.keyboard;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.gui.overlay.DiagnosticsOverlay;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public class KeyBindings {

    public static final KeyMapping modConfigurationMenu;
    public static final KeyMapping individualSoundConfigBinding;
    public static final KeyMapping diagnosticHud;

    static {
        var modMenuKey = Platform.isModLoaded(Constants.MODMENU) ? InputConstants.UNKNOWN.getValue() : InputConstants.KEY_EQUALS;
        modConfigurationMenu = registerKeyBinding(
                "dsurround.text.keybind.modConfigurationMenu",
                modMenuKey,
                "dsurround.text.keybind.section");

        individualSoundConfigBinding = registerKeyBinding(
                "dsurround.text.keybind.individualSoundConfig",
                InputConstants.UNKNOWN.getValue(),
                "dsurround.text.keybind.section");

        diagnosticHud = registerKeyBinding(
                "dsurround.text.keybind.diagnosticHud",
                InputConstants.UNKNOWN.getValue(),
                "dsurround.text.keybind.section");
    }

    private static KeyMapping registerKeyBinding(String translationKey, int code, String category) {
        var mapping = new KeyMapping(translationKey, code, category);
        KeyMappingRegistry.register(mapping);
        return mapping;
    }

    public static void register() {
        ClientState.TICK_END.register(KeyBindings::handleMenuKeyPress);
    }

    private static void handleMenuKeyPress(Minecraft client) {
        if (GameUtils.getCurrentScreen().isPresent() || GameUtils.getPlayer().isEmpty())
            return;

        if (modConfigurationMenu.consumeClick()) {
            var provider = ContainerManager.resolve(IConfigScreenFactoryProvider.class);
            var factory = provider.getModConfigScreenFactory(Configuration.class);
            if (factory.isPresent()) {
                GameUtils.setScreen(factory.get().create(null));
            } else {
                Library.LOGGER.info("Configuration GUI libraries not present");
            }
        }

        if (diagnosticHud.consumeClick())
            ContainerManager.resolve(DiagnosticsOverlay.class).toggleCollection();

        if (individualSoundConfigBinding.consumeClick()) {
            final boolean singlePlayer = GameUtils.isSinglePlayer();
            GameUtils.setScreen(new IndividualSoundControlScreen(null, singlePlayer));
            if (singlePlayer)
                ContainerManager.resolve(IAudioPlayer.class).stopAll();
        }
    }
}
