// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.orecruncher.dsurround.Constants;

/**
 * A row's reset button: a counterclockwise arrow that puts the row back as it was when the screen opened, or, with
 * Ctrl held, a bar under the arrow and back to the mod's defaults.
 * <p>
 * Vanilla's {@code SpriteIconButton} keeps one set of sprites, and the press doesn't say which modifiers were down,
 * so this draws its own. Which way it shows is set by the row each frame ({@link #setToDefault}); which way a press
 * goes is decided by the press's own modifiers, so a click always does what the Ctrl key says at that moment.
 */
public class ResetButton extends Button {

    public static final int SIZE = 20;

    private static final int SPRITE_WIDTH = 11;
    private static final int SPRITE_HEIGHT = 15;

    private static final WidgetSprites TO_START = sprites("reset");
    private static final WidgetSprites TO_DEFAULT = sprites("reset_default");

    // Narrated: vanilla's key binding "Reset", and ours for the defaults
    private static final Component RESET = Component.translatable("controls.reset");
    private static final Component RESET_DEFAULT = Component.translatable("dsurround.text.soundconfig.reset_default");

    private final Runnable toStart;
    private final Runnable toDefault;
    private boolean showsDefault;

    public ResetButton(Runnable toStart, Runnable toDefault) {
        super(0, 0, SIZE, SIZE, RESET, button -> {}, DEFAULT_NARRATION);
        this.toStart = toStart;
        this.toDefault = toDefault;
    }

    private static WidgetSprites sprites(String name) {
        return new WidgetSprites(
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sound_config/" + name),
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sound_config/" + name + "_disabled"));
    }

    /**
     * Whether the button shows the reset to the mod's defaults (Ctrl held) rather than to the screen's start.
     */
    public void setToDefault(boolean toDefault) {
        if (toDefault != this.showsDefault) {
            this.showsDefault = toDefault;
            this.setMessage(toDefault ? RESET_DEFAULT : RESET);
        }
    }

    /**
     * Whether the mouse is over the button, active or not (vanilla's {@code isMouseOver} ignores inactive widgets),
     * so its help shows even while there is nothing to reset.
     */
    public boolean isWithin(double mouseX, double mouseY) {
        return this.visible && mouseX >= this.getX() && mouseX < this.getRight() && mouseY >= this.getY() && mouseY < this.getBottom();
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        if (input.hasControlDown())
            this.toDefault.run();
        else
            this.toStart.run();
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractDefaultSprite(graphics);
        var sprites = this.showsDefault ? TO_DEFAULT : TO_START;
        int x = this.getX() + (this.getWidth() - SPRITE_WIDTH) / 2;
        int y = this.getY() + (this.getHeight() - SPRITE_HEIGHT) / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprites.get(this.isActive(), this.isHoveredOrFocused()), x, y, SPRITE_WIDTH, SPRITE_HEIGHT, this.alpha);
    }
}
