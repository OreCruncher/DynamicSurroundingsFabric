package org.orecruncher.dsurround.gui.sound;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.lib.gui.SliderControl;

/**
 * Edits a sound's volume scale, 0% (off) to 400%, in whole percent.
 */
public class VolumeSliderControl extends SliderControl {

    private static final int SLIDER_WIDTH = 100;
    private static final int SLIDER_HEIGHT = 20;

    private static final Component OFF = Component.translatable("options.off");

    private final IndividualSoundConfigEntry config;

    public VolumeSliderControl(IndividualSoundConfigEntry config) {
        super(0, 0, SLIDER_WIDTH, SLIDER_HEIGHT, 0F, 400F, 1, config.volumeScale);
        this.config = config;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        int percent = (int) this.getValue();
        this.setMessage(percent == 0 ? OFF : Component.literal(percent + "%"));
    }

    @Override
    protected void applyValue() {
        this.config.volumeScale = (int) this.getValue();
    }
}
