package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.Library;

public class SoundPlayButton extends Button {

    // These are 20x20 sprites
    private static final ResourceLocation PLAY_SYMBOL = new ResourceLocation(Library.MOD_ID, "controls/play");
    private static final ResourceLocation STOP_SYMBOL = new ResourceLocation(Library.MOD_ID, "controls/stop");

    private boolean isPlaying;

    public SoundPlayButton(int i, int j, OnPress onPress, Component component) {
        super(0, 0, 20, 20, component, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void playDownSound(@NotNull SoundManager ignored) {
        // Do nothing - we are suppressing the button click sound
    }

    public void play() {
        this.isPlaying = true;
    }

    public void stop() {
        this.isPlaying = false;
    }

    // Basically what ImageButton does but simplified.
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        ResourceLocation resourceLocation = this.getSpriteToRender();
        guiGraphics.blitSprite(resourceLocation, this.getX(), this.getY(), this.width, this.height);
    }

    private ResourceLocation getSpriteToRender() {
        return this.isPlaying ? STOP_SYMBOL : PLAY_SYMBOL;
    }
}
