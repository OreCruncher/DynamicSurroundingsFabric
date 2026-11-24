// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.gui.credits;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.orecruncher.dsurround.gui.TextPanel;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.markdown.MarkdownParser;
import org.orecruncher.dsurround.lib.markdown.Options;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The credits of Dynamic Surroundings and of every active pack that has its own ({@link CreditsFinder}): a list of
 * them down the left, the one picked on the right in a scrolling {@link TextPanel}, with links that show their hover
 * text and open (after vanilla's confirmation) when clicked. With one entry there is no list. Each document is
 * parsed when first picked.
 */
public class CreditsScreen extends Screen {

    // Space between the screen edges and the panels
    private static final int MARGIN_X = 20;
    // Space between the panels and the header/footer
    private static final int MARGIN_Y = 4;
    // Space between the list and the text
    private static final int GAP = 6;
    private static final int ROW_HEIGHT = 14;
    private static final int MIN_LIST_WIDTH = 80;
    // Padding within the list beside a name: the border's and the row's own
    private static final int LIST_PADDING = 16;
    // Vanilla's text area box (AbstractTextAreaWidget's), which the text panel draws: the list draws it too
    private static final WidgetSprites BACKGROUND_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/text_field"), Identifier.withDefaultNamespace("widget/text_field_highlighted"));

    private final Screen parent;
    private final List<CreditsFinder.CreditsEntry> entries;
    private final Map<CreditsFinder.CreditsEntry, Component> documents = new HashMap<>();
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private CreditsFinder.@Nullable CreditsEntry shown;
    // The panel the shown entry was put in: init makes a new one
    private @Nullable TextPanel shownOn;
    private @Nullable EntryList list;
    private TextPanel textPanel;

    /**
     * @param entries what to list, in order; the first is shown first. An empty list shows {@code emptyText}.
     */
    public CreditsScreen(Screen parent, Component title, List<CreditsFinder.CreditsEntry> entries, Component emptyText) {
        super(title);
        this.parent = parent;
        this.entries = entries.isEmpty() ? List.of(new CreditsFinder.CreditsEntry(title, emptyText.getString())) : List.copyOf(entries);
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(Button.BIG_WIDTH)
                .build());
        this.layout.visitWidgets(this::addRenderableWidget);

        var showing = this.shown != null ? this.shown : this.entries.getFirst();
        this.textPanel = this.addRenderableWidget(new TextPanel(this.font, Component.empty()));
        if (this.entries.size() > 1) {
            this.list = this.addRenderableWidget(new EntryList(this.minecraft));
            this.list.select(showing);
        } else {
            this.list = null;
        }

        this.repositionElements();
        this.show(showing);
        // Focus the text so the arrow and page keys scroll it straight away; Tab reaches the list
        this.setInitialFocus(this.textPanel);
    }

    /**
     * Called on resize (and when returning from the link confirmation screen). Only the sizes change, so the text
     * keeps its scroll position instead of being rebuilt.
     */
    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        int top = this.layout.getHeaderHeight() + MARGIN_Y;
        int height = this.layout.getContentHeight() - MARGIN_Y * 2;
        int textX = MARGIN_X;

        if (this.list != null) {
            int listWidth = this.listWidth();
            this.list.updateSizeAndPosition(listWidth, height, MARGIN_X, top);
            // Its scrollbar is drawn outside it on the right, as the text panel's is
            textX += listWidth + this.list.scrollbarWidth() + GAP;
        }

        // The scrollbar is drawn outside the panel on the right, so the panel leaves room for it
        int textWidth = this.width - textX - MARGIN_X - this.textPanel.scrollbarWidth();
        this.textPanel.resize(textWidth, height);
        this.textPanel.setPosition(textX, top);
    }

    /**
     * Wide enough for the longest name, within a third of the screen; longer names are cut short with an
     * ellipsis.
     */
    private int listWidth() {
        int widest = 0;
        for (var entry : this.entries)
            widest = Math.max(widest, this.font.width(entry.name()));
        return Math.clamp(widest + LIST_PADDING, MIN_LIST_WIDTH, Math.max(MIN_LIST_WIDTH, this.width / 3));
    }

    private void show(CreditsFinder.CreditsEntry entry) {
        // The list selects its row again on some keys and on init; that mustn't scroll the text back to its top
        if (entry == this.shown && this.shownOn == this.textPanel)
            return;
        this.shown = entry;
        this.shownOn = this.textPanel;
        var document = this.documents.computeIfAbsent(entry, e -> MarkdownParser.markdownToComponent(e.markdown(), Options.UNIFORM)
                .orElse(Component.empty()));
        this.textPanel.setBody(document);
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
        // The wheel scrolls the list over the list, and the text anywhere else
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
                || this.textPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * The names, one a row. Picking one (click, or the arrow keys once the list has focus) shows its document.
     */
    private final class EntryList extends ObjectSelectionList<EntryList.Row> {

        EntryList(Minecraft minecraft) {
            super(minecraft, 0, 0, 0, ROW_HEIGHT);
            for (var entry : CreditsScreen.this.entries)
                this.addEntry(new Row(entry));
        }

        void select(CreditsFinder.CreditsEntry entry) {
            for (var row : this.children()) {
                if (row.entry == entry) {
                    this.setSelected(row);
                    return;
                }
            }
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            if (row != null)
                CreditsScreen.this.show(row.entry);
        }

        @Override
        public int getRowWidth() {
            // The whole list inside its border
            return this.getWidth() - 4;
        }

        @Override
        public int getRowLeft() {
            return this.getX() + 2;
        }

        @Override
        protected int scrollBarX() {
            // Outside the border on the right, as the text panel's is
            return this.getRight();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // The scrollbar too, so the wheel over it scrolls the list
            return mouseX >= this.getX() && mouseX < this.getRight() + this.scrollbarWidth()
                    && mouseY >= this.getY() && mouseY < this.getBottom();
        }

        @Override
        protected void extractListBackground(@NotNull GuiGraphicsExtractor graphics) {
            // The text panel's own box, so the two look alike; its border lights up when the list has focus
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITES.get(this.active, this.isFocused()),
                    this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }

        @Override
        protected void enableScissor(@NotNull GuiGraphicsExtractor graphics) {
            // Rows scrolled part way out stop inside the border
            graphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getRight() - 1, this.getBottom() - 1);
        }

        @Override
        protected void extractListSeparators(@NotNull GuiGraphicsExtractor graphics) {
            // The list sits beside the text, not across the screen: no header and footer separators of its own
        }

        final class Row extends ObjectSelectionList.Entry<Row> {
            private final CreditsFinder.CreditsEntry entry;

            Row(CreditsFinder.CreditsEntry entry) {
                this.entry = entry;
            }

            @Override
            public void extractContent(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
                var font = CreditsScreen.this.font;
                int room = this.getContentWidth() - 4;
                FormattedCharSequence text;
                boolean cut = font.width(this.entry.name()) > room;
                if (cut) {
                    var ellipsis = CommonComponents.ELLIPSIS;
                    var shortened = font.substrByWidth(this.entry.name(), room - font.width(ellipsis));
                    text = Language.getInstance().getVisualOrder(FormattedText.composite(shortened, ellipsis));
                } else {
                    text = this.entry.name().getVisualOrderText();
                }
                graphics.text(font, text, this.getContentX() + 2, this.getContentYMiddle() - font.lineHeight / 2, 0xFFFFFFFF, true);
                if (cut && hovered)
                    graphics.setTooltipForNextFrame(this.entry.name(), mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
                EntryList.this.setSelected(this);
                return super.mouseClicked(event, doubleClick);
            }

            @Override
            public @NotNull Component getNarration() {
                return Component.translatable("narrator.select", this.entry.name());
            }
        }
    }
}
