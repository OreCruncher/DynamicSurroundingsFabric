package org.orecruncher.dsurround.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.markdown.MarkdownParser;
import org.orecruncher.dsurround.lib.markdown.Options;

import java.util.List;

/**
 * Shows a (possibly long) Component in a scrollable panel, with a Done button that returns to the parent screen.
 * Links in the text show their hover text and can be clicked.
 * <p>
 * Built from vanilla pieces: {@link HeaderAndFooterLayout} for the title and Done button, and an
 * {@link AbstractTextAreaWidget} for the scrolling, scrollbar, dragging, focus and narration. Link hover text comes
 * from drawing the text through the GUI's hover-aware text renderer.
 */
public class ComponentDisplayScreen extends Screen {

    // Space between the screen edges and the panel
    private static final int PANEL_MARGIN_X = 20;
    // Space between the panel and the header/footer
    private static final int PANEL_MARGIN_Y = 4;

    private final Screen parent;
    private final Component bodyToDisplay;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private TextPanel textPanel;

    protected ComponentDisplayScreen(Screen parent, Component title, Component bodyToDisplay) {
        super(title);
        this.parent = parent;
        this.bodyToDisplay = bodyToDisplay;
    }

    public static ComponentDisplayScreen createFromMarkdown(Screen parent, Component title, String markdownDocument) {
        Component document = MarkdownParser.markdownToComponent(markdownDocument, Options.UNIFORM)
                .orElse(Component.empty());
        return create(parent, title, document);
    }

    public static ComponentDisplayScreen create(Screen parent, Component title, Component component) {
        return new ComponentDisplayScreen(parent, title, component);
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        this.textPanel = this.layout.addToContents(new TextPanel(this.font, this.bodyToDisplay));
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(Button.BIG_WIDTH)
                .build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();

        // Focus the text so the arrow and page keys scroll it straight away
        this.setInitialFocus(this.textPanel);
    }

    /**
     * Called on resize (and when returning from the link confirmation screen). Only the sizes change, so the panel
     * keeps its text and scroll position instead of being rebuilt.
     */
    @Override
    protected void repositionElements() {
        int panelWidth = this.width - PANEL_MARGIN_X * 2 - this.textPanel.scrollbarWidth();
        int panelHeight = this.layout.getContentHeight() - PANEL_MARGIN_Y * 2;
        this.textPanel.resize(panelWidth, panelHeight);
        this.layout.arrangeElements();

        // The scrollbar is drawn outside the widget on the right. Shift left by half its width so the widget and
        // scrollbar together are centered.
        this.textPanel.setX(this.textPanel.getX() - this.textPanel.scrollbarWidth() / 2);
    }

    @Override
    public void onClose() {
        GameUtils.setScreen(this.parent);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Style style = this.textPanel.getStyleAt(event.x(), event.y());
            if (style != null && style.getClickEvent() != null) {
                // Vanilla's handling: a link asks for confirmation before opening
                defaultHandleClickEvent(style.getClickEvent(), this.minecraft, this);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // The wheel scrolls the text wherever the mouse is, not only over the panel
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
                || this.textPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * The scrolling text area. The text is wrapped to the widget's width and redrawn each frame; only lines that
     * are at least partly visible are drawn.
     */
    private static final class TextPanel extends AbstractTextAreaWidget {

        private final Font font;
        private final Component body;
        private List<FormattedCharSequence> lines = List.of();
        private int wrapWidth = -1;

        TextPanel(Font font, Component body) {
            super(0, 0, 0, 0, body, AbstractScrollArea.defaultSettings(font.lineHeight * 2));
            this.font = font;
            this.body = body;
        }

        /**
         * Sets the size, re-wraps the text if the width changed, and keeps the reader at the same relative place
         * in the document.
         */
        void resize(int width, int height) {
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
        Style getStyleAt(double mouseX, double mouseY) {
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
}
