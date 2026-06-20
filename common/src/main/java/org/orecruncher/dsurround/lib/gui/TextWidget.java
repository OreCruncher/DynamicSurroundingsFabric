package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import net.minecraft.client.gui.ActiveTextCollector;

public class TextWidget extends AbstractStringWidget {

    public TextWidget(int x, int y, int width, int height, Component component, Font font) {
        super(x, y, width, height, component, font);
    }

    @Override
    public void visitLines(@NotNull ActiveTextCollector output) {
        // Text is extracted directly below so the widget can apply clipping.
    }

    @Override
    public void extractWidgetRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        int y = getY();

        int nameWidth = this.getFont().width(this.getMessage());
        if (nameWidth > getWidth()) {
            guiGraphics.enableScissor(getX(), y, getX() + getWidth(), y + this.getFont().lineHeight);
            try {
                guiGraphics.text(this.getFont(), this.getMessage(), getX(), y, 0xFFFFFFFF, false);
            } finally {
                guiGraphics.disableScissor();
            }
        } else {
            guiGraphics.text(this.getFont(), this.getMessage(), getX(), y, 0xFFFFFFFF, false);
        }
    }
}
