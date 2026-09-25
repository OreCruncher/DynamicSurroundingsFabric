package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class TextWidget extends AbstractStringWidget {

    public TextWidget(int x, int y, int width, int height, Component component, Font font) {
        super(x, y, width, height, component, font);
    }

    @Override
    public void visitLines(@NonNull ActiveTextCollector output) {
        int nameWidth = this.getFont().width(this.getMessage());

        if (nameWidth > getWidth()) {
            output.acceptScrollingWithDefaultCenter(this.getMessage(), this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getFont().lineHeight);
        } else {
            output.accept(getX(), getY(), this.getMessage());
        }
    }
}
