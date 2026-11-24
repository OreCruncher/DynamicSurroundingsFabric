// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A scrolling text area for a (possibly long) Component, built on vanilla's {@link AbstractTextAreaWidget} for the
 * scrolling, scrollbar, dragging, focus and narration. The text is wrapped to the widget's width and redrawn each
 * frame; only lines that are at least partly visible are drawn. The screen holding it handles link clicks, through
 * {@link #getStyleAt}.
 */
public final class TextPanel extends AbstractTextAreaWidget {

    private final Font font;
    private Component body;
    private List<FormattedCharSequence> lines = List.of();
    private int wrapWidth = -1;

    public TextPanel(Font font, Component body) {
        super(0, 0, 0, 0, body, AbstractScrollArea.defaultSettings(font.lineHeight * 2));
        this.font = font;
        this.body = body;
    }

    /**
     * Sets the size, re-wraps the text if the width changed, and keeps the reader at the same relative place
     * in the document.
     */
    public void resize(int width, int height) {
        int maxScroll = this.maxScrollAmount();
        double scrollRatio = maxScroll > 0 ? this.scrollAmount() / maxScroll : 0.0;

        this.setSize(Math.max(1, width), Math.max(1, height));
        int newWrapWidth = Math.max(1, this.width - this.totalInnerPadding());
        if (newWrapWidth != this.wrapWidth) {
            this.wrapWidth = newWrapWidth;
            this.lines = this.font.split(this.body, newWrapWidth);
        }

        this.setScrollAmount(scrollRatio * this.maxScrollAmount());
    }

    /**
     * Shows other text, from its top. Wrapped to the current width, so it can be called before or after
     * {@link #resize}.
     */
    public void setBody(Component body) {
        this.body = body;
        this.setMessage(body);
        this.lines = this.wrapWidth > 0 ? this.font.split(body, this.wrapWidth) : List.of();
        this.setScrollAmount(0);
    }

    @Override
    protected int getInnerHeight() {
        return this.lines.size() * this.font.lineHeight;
    }

    /**
     * Same as the vanilla version except that the text is moved by a whole number of pixels. Dragging the
     * scrollbar produces fractional scroll amounts, and text drawn between pixels can shimmer.
     */
    @Override
    public void extractWidgetRenderState(@NotNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }
        this.extractBackground(context);
        context.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
        context.pose().pushMatrix();
        context.pose().translate(0.0f, -this.renderedScrollAmount());
        this.extractContents(context, mouseX, mouseY, delta);
        context.pose().popMatrix();
        context.disableScissor();
        this.extractScrollbar(context, mouseX, mouseY);
        this.extractDecorations(context);
    }

    /**
     * Draws the visible lines. They go through the hover-aware text renderer, which shows a link's hover text
     * and changes the cursor over it; it is created here so it has the scroll offset and scissor.
     */
    @Override
    protected void extractContents(@NotNull GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        var text = context.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR);
        int x = this.getX() + this.innerPadding();
        int y = this.getY() + this.innerPadding();
        for (var line : this.lines) {
            if (this.withinContentAreaTopBottom(y, y + this.font.lineHeight)) {
                text.accept(x, y, line);
            }
            y += this.font.lineHeight;
        }
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (this.scrollable()) {
            int page = Math.max(this.font.lineHeight, this.height - this.totalInnerPadding() - this.font.lineHeight);
            switch (event.key()) {
                case InputConstants.KEY_PAGEUP -> this.setScrollAmount(this.scrollAmount() - page);
                case InputConstants.KEY_PAGEDOWN -> this.setScrollAmount(this.scrollAmount() + page);
                case InputConstants.KEY_HOME -> this.setScrollAmount(0);
                case InputConstants.KEY_END -> this.setScrollAmount(this.maxScrollAmount());
                default -> {
                    // Up and down are handled by the vanilla widget
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.body);
    }

    /**
     * The style of the clickable text under the mouse, or null if the mouse isn't over any.
     */
    @Nullable
    public Style getStyleAt(double mouseX, double mouseY) {
        if (!this.visible || !this.withinContentArea(mouseX, mouseY)) {
            return null;
        }

        double relativeX = mouseX - (this.getX() + this.innerPadding());
        if (relativeX < 0) {
            // Left padding. componentStyleAtWidth would report the line's first character here.
            return null;
        }

        double relativeY = mouseY - (this.getY() + this.innerPadding()) + this.renderedScrollAmount();
        // floor, not a cast: a cast rounds -0.5 up to row 0
        int row = Mth.floor(relativeY / this.font.lineHeight);
        if (row < 0 || row >= this.lines.size()) {
            return null;
        }
        // Where the line is drawn on screen, tested against the mouse the way vanilla tests clickable text
        int lineX = this.getX() + this.innerPadding();
        int lineY = this.getY() + this.innerPadding() + row * this.font.lineHeight - this.renderedScrollAmount();
        var finder = new ActiveTextCollector.ClickableStyleFinder(this.font, Mth.floor(mouseX), Mth.floor(mouseY));
        finder.accept(lineX, lineY, this.lines.get(row));
        return finder.result();
    }

    private boolean withinContentArea(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height;
    }

    /**
     * The scroll offset actually used for drawing and hit testing: the scroll amount rounded to a whole pixel.
     * The unrounded amount is kept so small drags still add up.
     */
    private int renderedScrollAmount() {
        return (int) Math.round(this.scrollAmount());
    }
}
