package org.orecruncher.dsurround.gui.keyboard;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.orecruncher.dsurround.eventing.handlers.DiagnosticHandler;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

@Environment(EnvType.CLIENT)
public class KeyBindings {

    public static final KeyBinding individualSoundConfigBinding;
    public static final KeyBinding diagnosticHud;

    static {
        individualSoundConfigBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "dsurround.text.keybind.individualSoundConfig",
                        InputUtil.UNKNOWN_KEY.getCode(),
                        "dsurround.text.keybind.section"
                ));

        diagnosticHud = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "dsurround.text.keybind.diagnosticHud",
                        InputUtil.UNKNOWN_KEY.getCode(),
                        "dsurround.text.keybind.section"
                ));
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (GameUtils.getCurrentScreen() == null && GameUtils.getPlayer() != null) {
                if (individualSoundConfigBinding.wasPressed()) {
                    final boolean singlePlayer = GameUtils.isSinglePlayer();
                    GameUtils.setScreen(new IndividualSoundControlScreen(null, singlePlayer));
                    if (singlePlayer)
                        ContainerManager.resolve(IAudioPlayer.class).stopAll();
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (GameUtils.getCurrentScreen() == null && GameUtils.getPlayer() != null) {
                if (diagnosticHud.wasPressed())
                    DiagnosticHandler.toggleCollection();
            }
        });
    }
}
