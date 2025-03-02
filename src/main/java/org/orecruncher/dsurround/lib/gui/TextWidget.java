package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class TextWidget extends AbstractStringWidget {

    public TextWidget(int x, int y, int width, int height, Component component, Font font) {
        super(x, y, width, height, component, font);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int i, int j, float f) {
        int y = getY();

        int nameWidth = this.getFont().width(this.getMessage());
        if (nameWidth > getWidth()) {
            renderScrollingString(guiGraphics, this.getFont(), this.getMessage(), getX(), y, getX() + getWidth(), y + this.getFont().lineHeight, -1);
        } else {
            guiGraphics.drawString(this.getFont(), this.getMessage(), getX(), y, 0xFFFFFF);
        }
    }
}
