package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.mixins.core.MixinButtonWidget;

public class SilentButton extends Button {

    protected SilentButton(Button sourceButton) {
        this(sourceButton.getX(), sourceButton.getY(), sourceButton.getWidth(), sourceButton.getHeight(), sourceButton.getMessage(), ((MixinButtonWidget)sourceButton).dsurround_getPressAction(), ((MixinButtonWidget)sourceButton).dsurround_getNarrationSupplier());
    }

    protected SilentButton(int x, int y, int width, int height, Component message, OnPress onPress, CreateNarration narrationSupplier) {
        super(x, y, width, height, message, onPress, narrationSupplier);
    }

    @Override
    public void playDownSound(SoundManager ignored) {
        // Do nothing - we are suppressing the down sound
    }

    public static SilentButton from(Button buttonWidget) {
        return new SilentButton(buttonWidget);
    }
}
