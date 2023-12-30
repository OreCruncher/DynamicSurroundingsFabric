package org.orecruncher.dsurround.gui.keyboard;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.orecruncher.dsurround.gui.hud.DiagnosticsOverlay;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.events.ClientState;
import org.orecruncher.dsurround.sound.IAudioPlayer;

public class KeyBindings {

    public static final KeyBinding individualSoundConfigBinding;
    public static final KeyBinding diagnosticHud;

    static {
        var platform = Library.getPlatform();

        individualSoundConfigBinding = platform.registerKeyBinding(
                "dsurround.text.keybind.individualSoundConfig",
                InputUtil.UNKNOWN_KEY.getCode(),
                "dsurround.text.keybind.section");

        diagnosticHud = platform.registerKeyBinding(
                "dsurround.text.keybind.diagnosticHud",
                InputUtil.UNKNOWN_KEY.getCode(),
                "dsurround.text.keybind.section");
    }

    public static void register() {
        ClientState.TICK_END.register(client -> {
            if (GameUtils.getCurrentScreen().isEmpty())
                GameUtils.getPlayer()
                        .ifPresent(p -> {
                            if (individualSoundConfigBinding.wasPressed()) {
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
                            if (diagnosticHud.wasPressed())
                                ContainerManager.resolve(DiagnosticsOverlay.class).toggleCollection();
                        });
        });
    }
}
