package org.orecruncher.dsurround.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.markdown.MarkdownHelper;
import org.orecruncher.dsurround.lib.markdown.ParserOptions;

import java.util.List;

public class ComponentDisplayScreen extends Screen {

    // Layout bounds (can be dynamic percentages or fixed margins)
    private static final int PANEL_MARGIN_X = 20;
    private static final int PANEL_MARGIN_Y = 50;
    private static final int BOTTOM_WIDGET_HEIGHT = 50;
    private static final int SPLITWIDTH_OFFSET = 24;        // Accounts for scrollbar gutter

    private final Screen parent;
    private final Component bodyToDisplay;
    private double scrollAmount = 0.0;
    private int maxScrollY = 0;
    private int lastSplitWidth = -1;
    private List<FormattedCharSequence> splitLines;

    protected ComponentDisplayScreen(Screen parent, Component title, Component bodyToDisplay) {
        super(title);
        this.parent = parent;
        this.bodyToDisplay = bodyToDisplay;
        this.splitLines = List.of();
    }

    @Override
    protected void init() {
        super.init();

        // Add standard exit button centered at the bottom
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, button -> this.close())
                        .bounds(this.width / 2 - 50, this.height - 35, 100, 20)
                        .build()
        );

        this.resplitLines(this.getPanelWidth() - SPLITWIDTH_OFFSET);
    }

    @Override
    public void resize(@NotNull Minecraft client, int width, int height) {
        double scrollRatio = this.maxScrollY > 0 ? this.scrollAmount / this.maxScrollY : 0.0;
        super.resize(client, width, height);
        this.scrollAmount = scrollRatio * this.maxScrollY;
        this.resplitLines(this.getPanelWidth() - SPLITWIDTH_OFFSET);
    }

    @Override
    public void render(@NotNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Draw screen title
        context.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        int panelX = this.getPanelX();
        int panelY = this.getPanelY();
        int panelWidth = this.getPanelWidth();
        int panelHeight = this.getPanelHeight();

        var wrappedLines = this.splitLines;

        int totalTextHeight = wrappedLines.size() * this.font.lineHeight;
        this.maxScrollY = Math.max(0, totalTextHeight - (panelHeight - 20));
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, this.maxScrollY);

        // Draw panel background and border
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, ColorPalette.NEAR_BLACK.getValue() | 0xC0000000);
        context.renderOutline(panelX, panelY, panelWidth, panelHeight, ColorPalette.GRAY.getValue() | 0xFF000000);

        // Render text lines inside scissor bounds
        context.enableScissor(panelX + 1, panelY + 1, panelX + panelWidth - 14, panelY + panelHeight - 1);
        context.pose().pushPose();
        context.pose().translate(panelX + 10, (panelY + 10) - (float) this.scrollAmount, 0.0f);

        int currentY = 0;
        for (var line : wrappedLines) {
            context.drawString(this.font, line, 0, currentY, 0xFFFFFFFF, true);
            currentY += this.font.lineHeight;
        }

        context.pose().popPose();
        context.disableScissor();

        // Render scrollbar handle if scrollable
        if (this.maxScrollY > 0) {
            int scrollbarX = panelX + panelWidth - 6;
            int scrollbarHeight = Math.max(20, (panelHeight * panelHeight) / (totalTextHeight + panelHeight));
            int scrollbarY = panelY + (int) ((this.scrollAmount / this.maxScrollY) * (panelHeight - scrollbarHeight));

            context.fill(scrollbarX, panelY + 1, scrollbarX + 4, panelY + panelHeight - 1, ColorPalette.CHARCOAL.getValue() | 0xFF000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, ColorPalette.GRAY.getValue() | 0xFF000000);
        }

        // Render tooltip if hovering over an interactive style element
        Style hoveredStyle = this.getStyleAt(mouseX, mouseY);
        if (hoveredStyle != null && hoveredStyle.getHoverEvent() != null) {
            HoverEvent hoverEvent = hoveredStyle.getHoverEvent();
            var value = hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT);
            if (value != null) {
                context.renderTooltip(this.font, value, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Style style = getStyleAt(mouseX, mouseY);
            if (style != null && this.handleComponentClicked(style)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScrollY > 0) {
            this.scrollAmount = Mth.clamp(this.scrollAmount - (verticalAmount * 16), 0.0, this.maxScrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.maxScrollY > 0 && button == 0) {
            double scrollFactor = (double) this.maxScrollY / getPanelHeight();
            this.scrollAmount = Mth.clamp(this.scrollAmount + (deltaY * scrollFactor), 0.0, this.maxScrollY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    // --- REFACTORED LAYOUT & HIT-TEST HELPERS ---
    private void resplitLines(int newWidth) {
        if (this.lastSplitWidth != newWidth) {
            this.lastSplitWidth = newWidth;
            this.splitLines = this.font.split(this.bodyToDisplay, this.lastSplitWidth);
        }
    }

    private int getPanelX() { return PANEL_MARGIN_X; }
    private int getPanelY() { return PANEL_MARGIN_Y; }
    private int getPanelWidth() { return this.width - (PANEL_MARGIN_X * 2); }
    private int getPanelHeight() { return this.height - PANEL_MARGIN_Y - BOTTOM_WIDGET_HEIGHT; }

    private Style getStyleAt(double mouseX, double mouseY) {
        int panelX = this.getPanelX();
        int panelY = this.getPanelY();
        int panelWidth = this.getPanelWidth();
        int panelHeight = this.getPanelHeight();

        // Check if mouse is within panel bounds
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            var wrappedLines = this.splitLines;

            double relativeX = mouseX - (panelX + 10);
            double relativeY = (mouseY - (panelY + 10)) + this.scrollAmount;
            int row = (int) (relativeY / this.font.lineHeight);

            if (row >= 0 && row < wrappedLines.size()) {
                var line = wrappedLines.get(row);
                return this.font.getSplitter().componentStyleAtWidth(line, (int) relativeX);
            }
        }
        return null;
    }

    public void close() {
        GameUtils.setScreen(this.parent);
    }

    public static ComponentDisplayScreen createFromMarkdown(Screen parent, Component title, String markdownDocument) {
        Component document = MarkdownHelper.markdownToComponent(markdownDocument, ParserOptions.UNIFORM)
                .orElse(Component.literal("Could not translate markdown document"));
        return create(parent, title, document);
    }

    public static ComponentDisplayScreen create(Screen parent, Component title, Component component) {
        return new ComponentDisplayScreen(parent, title, component);
    }
}