package org.orecruncher.dsurround.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.markdown.MarkdownParser;
import org.orecruncher.dsurround.lib.markdown.Options;

import java.util.List;

/**
 * Shows a (possibly long) Component in a scrollable panel, with a Done button that returns to the parent screen.
 * Links in the text show their hover text and can be clicked.
 */
public class ComponentDisplayScreen extends Screen {

    // Panel placement within the screen
    private static final int PANEL_MARGIN_X = 20;
    private static final int PANEL_MARGIN_Y = 50;
    private static final int BOTTOM_WIDGET_HEIGHT = 50;

    // Space between the panel border and the text
    private static final int TEXT_PADDING = 10;
    // Space reserved on the right of the panel for the scrollbar; the text is clipped at its left edge
    private static final int SCROLLBAR_GUTTER = 14;
    private static final int SCROLLBAR_WIDTH = 4;
    // Gap between the scrollbar and the panel's right border
    private static final int SCROLLBAR_INSET = 2;
    // Extra pixels either side of the scrollbar that still count as grabbing it
    private static final int SCROLLBAR_GRAB_MARGIN = 2;
    private static final int MIN_THUMB_HEIGHT = 20;

    private final Screen parent;
    private final Component bodyToDisplay;
    private List<FormattedCharSequence> splitLines = List.of();
    private int lastSplitWidth = -1;
    private int totalTextHeight = 0;
    private int maxScrollY = 0;
    private double scrollAmount = 0.0;
    private boolean draggingScrollbar = false;

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
        super.init();

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                        .bounds(this.width / 2 - 50, this.height - 35, 100, 20)
                        .build()
        );

        this.layoutText();
    }

    @Override
    public void resize(@NotNull Minecraft client, int width, int height) {
        // Keep the reader at the same relative place in the document. super.resize() calls init(), which re-wraps
        // the text and recalculates maxScrollY for the new size.
        double scrollRatio = this.maxScrollY > 0 ? this.scrollAmount / this.maxScrollY : 0.0;
        super.resize(client, width, height);
        this.scrollTo(scrollRatio * this.maxScrollY);
    }

    @Override
    public void onClose() {
        GameUtils.setScreen(this.parent);
    }

    // ---- Rendering -------------------------------------------------------------------------------------------

    @Override
    public void render(@NotNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Screen.render draws the background, then the widgets
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        int panelX = this.getPanelX();
        int panelY = this.getPanelY();
        int panelWidth = this.getPanelWidth();
        int panelHeight = this.getPanelHeight();

        // Window too small to show anything useful
        if (panelWidth <= SCROLLBAR_GUTTER + TEXT_PADDING || panelHeight <= 2 * TEXT_PADDING) {
            return;
        }

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, ColorPalette.NEAR_BLACK.getValue() | 0xC0000000);
        context.renderOutline(panelX, panelY, panelWidth, panelHeight, ColorPalette.GRAY.getValue() | 0xFF000000);

        context.enableScissor(this.getTextClipLeft(), this.getTextClipTop(), this.getTextClipRight(), this.getTextClipBottom());
        context.pose().pushPose();
        context.pose().translate(this.getTextX(), this.getTextY() - (float) this.scrollAmount, 0.0f);

        int currentY = 0;
        for (var line : this.splitLines) {
            context.drawString(this.font, line, 0, currentY, 0xFFFFFFFF, true);
            currentY += this.font.lineHeight;
        }

        context.pose().popPose();
        context.disableScissor();

        if (this.maxScrollY > 0) {
            int scrollbarX = this.getScrollbarX();
            int thumbY = this.getThumbY();
            context.fill(scrollbarX, this.getTrackTop(), scrollbarX + SCROLLBAR_WIDTH, this.getTrackBottom(), ColorPalette.CHARCOAL.getValue() | 0xFF000000);
            context.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + this.getThumbHeight(), ColorPalette.GRAY.getValue() | 0xFF000000);
        }

        Style hoveredStyle = this.getStyleAt(mouseX, mouseY);
        if (hoveredStyle != null) {
            context.renderComponentHoverEffect(this.font, hoveredStyle, mouseX, mouseY);
        }
    }

    // ---- Input -----------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (this.isOverScrollbar(mouseX, mouseY)) {
                // Clicking the track outside the thumb jumps there, centering the thumb on the mouse
                int thumbY = this.getThumbY();
                if (mouseY < thumbY || mouseY >= thumbY + this.getThumbHeight()) {
                    double thumbTop = mouseY - this.getThumbHeight() / 2.0 - this.getTrackTop();
                    this.scrollTo(thumbTop * this.getScrollPerThumbPixel());
                }
                this.draggingScrollbar = true;
                return true;
            }
            Style style = this.getStyleAt(mouseX, mouseY);
            if (style != null && this.handleComponentClicked(style)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.draggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingScrollbar && button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.scrollTo(this.scrollAmount + deltaY * this.getScrollPerThumbPixel());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScrollY > 0) {
            this.scrollTo(this.scrollAmount - verticalAmount * this.font.lineHeight * 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.maxScrollY > 0) {
            int page = Math.max(this.font.lineHeight, this.getVisibleTextHeight() - this.font.lineHeight);
            switch (keyCode) {
                case InputConstants.KEY_UP -> this.scrollTo(this.scrollAmount - this.font.lineHeight);
                case InputConstants.KEY_DOWN -> this.scrollTo(this.scrollAmount + this.font.lineHeight);
                case InputConstants.KEY_PAGEUP -> this.scrollTo(this.scrollAmount - page);
                case InputConstants.KEY_PAGEDOWN -> this.scrollTo(this.scrollAmount + page);
                case InputConstants.KEY_HOME -> this.scrollTo(0);
                case InputConstants.KEY_END -> this.scrollTo(this.maxScrollY);
                default -> {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- Layout ----------------------------------------------------------------------------------------------

    /**
     * Re-wraps the text if the available width changed, and recalculates how far the text can scroll. Called
     * whenever the screen is (re)initialized, so render and the input handlers always see a consistent layout.
     */
    private void layoutText() {
        int wrapWidth = Math.max(1, this.getPanelWidth() - TEXT_PADDING - SCROLLBAR_GUTTER);
        if (this.lastSplitWidth != wrapWidth) {
            this.lastSplitWidth = wrapWidth;
            this.splitLines = this.font.split(this.bodyToDisplay, wrapWidth);
        }
        this.totalTextHeight = this.splitLines.size() * this.font.lineHeight;
        this.maxScrollY = Math.max(0, this.totalTextHeight - this.getVisibleTextHeight());
        this.scrollTo(this.scrollAmount);
    }

    private void scrollTo(double amount) {
        this.scrollAmount = Mth.clamp(amount, 0.0, this.maxScrollY);
    }

    private int getPanelX() {
        return PANEL_MARGIN_X;
    }

    private int getPanelY() {
        return PANEL_MARGIN_Y;
    }

    private int getPanelWidth() {
        return this.width - PANEL_MARGIN_X * 2;
    }

    private int getPanelHeight() {
        return this.height - PANEL_MARGIN_Y - BOTTOM_WIDGET_HEIGHT;
    }

    private int getTextX() {
        return this.getPanelX() + TEXT_PADDING;
    }

    private int getTextY() {
        return this.getPanelY() + TEXT_PADDING;
    }

    private int getVisibleTextHeight() {
        return Math.max(0, this.getPanelHeight() - 2 * TEXT_PADDING);
    }

    // The text is clipped to the inside of the border, stopping at the scrollbar gutter

    private int getTextClipLeft() {
        return this.getPanelX() + 1;
    }

    private int getTextClipTop() {
        return this.getPanelY() + 1;
    }

    private int getTextClipRight() {
        return this.getPanelX() + this.getPanelWidth() - SCROLLBAR_GUTTER;
    }

    private int getTextClipBottom() {
        return this.getPanelY() + this.getPanelHeight() - 1;
    }

    // ---- Scrollbar geometry ----------------------------------------------------------------------------------

    private int getScrollbarX() {
        return this.getPanelX() + this.getPanelWidth() - SCROLLBAR_INSET - SCROLLBAR_WIDTH;
    }

    private int getTrackTop() {
        return this.getPanelY() + 1;
    }

    private int getTrackBottom() {
        return this.getPanelY() + this.getPanelHeight() - 1;
    }

    private int getTrackHeight() {
        return Math.max(0, this.getTrackBottom() - this.getTrackTop());
    }

    /**
     * The thumb's share of the track matches the visible share of the text, but never smaller than
     * {@link #MIN_THUMB_HEIGHT} (or larger than the track).
     */
    private int getThumbHeight() {
        int trackHeight = this.getTrackHeight();
        if (this.totalTextHeight <= 0) {
            return trackHeight;
        }
        int proportional = (int) ((long) this.getVisibleTextHeight() * trackHeight / this.totalTextHeight);
        return Mth.clamp(proportional, Math.min(MIN_THUMB_HEIGHT, trackHeight), trackHeight);
    }

    private int getThumbY() {
        if (this.maxScrollY <= 0) {
            return this.getTrackTop();
        }
        int travel = this.getTrackHeight() - this.getThumbHeight();
        return this.getTrackTop() + (int) (this.scrollAmount / this.maxScrollY * travel);
    }

    /**
     * How far the text scrolls when the thumb moves one pixel.
     */
    private double getScrollPerThumbPixel() {
        int travel = this.getTrackHeight() - this.getThumbHeight();
        return travel > 0 ? (double) this.maxScrollY / travel : 0.0;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (this.maxScrollY <= 0) {
            return false;
        }
        int scrollbarX = this.getScrollbarX();
        return mouseX >= scrollbarX - SCROLLBAR_GRAB_MARGIN && mouseX < scrollbarX + SCROLLBAR_WIDTH + SCROLLBAR_GRAB_MARGIN
                && mouseY >= this.getTrackTop() && mouseY < this.getTrackBottom();
    }

    // ---- Hit testing -----------------------------------------------------------------------------------------

    /**
     * The style of the text under the mouse, or null if the mouse isn't over visible text.
     */
    private @Nullable Style getStyleAt(double mouseX, double mouseY) {
        // Only the area the text is actually drawn in (the scissor rectangle) counts
        if (mouseX < this.getTextClipLeft() || mouseX >= this.getTextClipRight()
                || mouseY < this.getTextClipTop() || mouseY >= this.getTextClipBottom()) {
            return null;
        }

        double relativeX = mouseX - this.getTextX();
        if (relativeX < 0) {
            // Left padding. componentStyleAtWidth would report the line's first character here.
            return null;
        }

        double relativeY = mouseY - this.getTextY() + this.scrollAmount;
        // floor, not a cast: a cast rounds -0.5 up to row 0
        int row = Mth.floor(relativeY / this.font.lineHeight);
        if (row < 0 || row >= this.splitLines.size()) {
            return null;
        }
        return this.font.getSplitter().componentStyleAtWidth(this.splitLines.get(row), Mth.floor(relativeX));
    }
}
