package org.orecruncher.dsurround.gui.keyboard;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.orecruncher.dsurround.gui.hud.DiagnosticsOverlay;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.events.ClientState;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public class KeyBindings {

    public static final KeyMapping individualSoundConfigBinding;
    public static final KeyMapping diagnosticHud;

    static {
        var platform = Library.getPlatform();

        individualSoundConfigBinding = platform.registerKeyBinding(
                "dsurround.text.keybind.individualSoundConfig",
                InputConstants.UNKNOWN.getValue(),
                "dsurround.text.keybind.section");

        diagnosticHud = platform.registerKeyBinding(
                "dsurround.text.keybind.diagnosticHud",
                InputConstants.UNKNOWN.getValue(),
                "dsurround.text.keybind.section");
    }

    public static void register() {
        ClientState.TICK_END.register(client -> {
            if (GameUtils.getCurrentScreen().isEmpty())
                GameUtils.getPlayer()
                        .ifPresent(p -> {
                            if (individualSoundConfigBinding.consumeClick()) {
                                final boolean singlePlayer = GameUtils.isSinglePlayer();
                                GameUtils.setScreen(new IndividualSoundControlScreen(null, singlePlayer));
                                if (singlePlayer)
                                    ContainerManager.resolve(IAudioPlayer.class).stopAll();
                            }
                        });
        });

        ClientState.TICK_END.register(client -> {
            if (GameUtils.getCurrentScreen().isEmpty())
                GameUtils.getPlayer()
                        .ifPresent(p -> {
                            if (diagnosticHud.consumeClick())
                                ContainerManager.resolve(DiagnosticsOverlay.class).toggleCollection();
                        });
        });
    }
}
