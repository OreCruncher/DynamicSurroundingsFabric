package org.orecruncher.dsurround.gui.sound;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.orecruncher.dsurround.lib.gui.SliderControl;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;

public class VolumeSliderControl extends SliderControl {

    private static final int SLIDER_WIDTH = 100;
    private static final int SLIDER_HEIGHT = 20;

    private static final DecimalFormat FORMAT;

    private static final Component OFF = Component.translatable("options.off");

    static {
        FORMAT = new DecimalFormat("0");
        FORMAT.setRoundingMode(RoundingMode.HALF_UP);
        FORMAT.setDecimalSeparatorAlwaysShown(false);
    }

    private final IndividualSoundControlListEntry entry;

    public VolumeSliderControl(IndividualSoundControlListEntry entry, int x, int y) {
        this(entry, x, y, ImmutableList.of());
    }

    public VolumeSliderControl(IndividualSoundControlListEntry entry, int x, int y, List<FormattedCharSequence> toolTip) {
        super(x, y, SLIDER_WIDTH, SLIDER_HEIGHT, 0F, 400F, 1, entry.getData().volumeScale, toolTip);
        this.entry = entry;
        this.updateMessage();
    }

    protected void updateMessage() {
        Component text = this.getValue() == 0 ? OFF : Component.literal(FORMAT.format(this.getValue()) + "%");
        this.setMessage(text);
    }

    @Override
    protected void applyValue() {
        this.entry.getData().volumeScale = (int) this.getValue();
    }
}
