package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.gui.ToggleButton;

public class SoundPlayButton extends ToggleButton {

    // These are 20x20 sprites
    private static final Identifier PLAY_SYMBOL = Identifier.fromNamespaceAndPath(Library.MOD_ID, "controls/play");
    private static final Identifier STOP_SYMBOL = Identifier.fromNamespaceAndPath(Library.MOD_ID, "controls/stop");

    public SoundPlayButton(OnPress onPress) {
        super(false, STOP_SYMBOL, PLAY_SYMBOL, onPress);
    }

    @Override
    public void playDownSound(@NotNull SoundManager ignored) {
        // Do nothing - we are suppressing the button click sound
    }

    public void play() {
        this.setOn(true);
    }

    public void stop() {
        this.setOn(false);
    }
}
