package org.orecruncher.dsurround.lib.config.compat;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.gui.TooltipDelay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A row of buttons on a Cloth config screen, after a name: one per preset. Cloth has no entry for buttons. Holds no
 * value: a button's action does its own saving.
 */
class PresetButtonsEntry extends TooltipListEntry<Object> {

    private static final int GAP = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PER_ROW = 5;
    // Vanilla's small buttons are 98 to 150 wide; wider looks stretched on a large screen
    private static final int MAX_WIDTH = 120;

    /**
     * A button's label, its tooltip, and what pressing it does.
     */
    record Choice(Component label, Component[] tooltip, Runnable action) {
    }

    private final Component[] tooltip;
    // Where the buttons start after the name: the same in every row, so rows line up
    private final int nameWidth;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Component[]> buttonTooltips = new ArrayList<>();
    // Each button's tooltip shows only once the mouse has rested on it
    private final List<TooltipDelay> delays = new ArrayList<>();
    private final TooltipDelay nameDelay = new TooltipDelay();

    /**
     * @param nameWidth how wide a name to leave room for: the widest of the rows', so their buttons line up
     */
    PresetButtonsEntry(Component name, Component[] tooltip, List<Choice> choices, int nameWidth) {
        super(name, Optional::empty);
        this.tooltip = tooltip;
        this.nameWidth = nameWidth;
        for (var choice : choices) {
            this.buttons.add(Button.builder(choice.label(), b -> choice.action().run()).size(0, BUTTON_HEIGHT).build());
            this.buttonTooltips.add(choice.tooltip());
            this.delays.add(new TooltipDelay());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        var font = Minecraft.getInstance().font;
        var name = this.getDisplayedFieldName();
        graphics.drawString(font, name, x, y + 6, this.getPreferredTextColor());

        // The buttons share what is left of the row after the name, up to PER_ROW a row (a modpack can add presets)
        // and MAX_WIDTH each, starting just after the name
        int left = x + Math.max(this.nameWidth, font.width(name)) + 8;
        int columns = Math.min(this.buttons.size(), PER_ROW);
        int width = Math.min(MAX_WIDTH, (x + entryWidth - left - GAP * (columns - 1)) / columns);
        for (int i = 0; i < this.buttons.size(); i++) {
            var button = this.buttons.get(i);
            button.active = this.isEditable();
            button.setX(left + (i % PER_ROW) * (width + GAP));
            button.setY(y + (i / PER_ROW) * (BUTTON_HEIGHT + GAP));
            button.setWidth(width);
            button.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public int getItemHeight() {
        int rows = (this.buttons.size() + PER_ROW - 1) / PER_ROW;
        return Math.max(1, rows) * (BUTTON_HEIGHT + GAP) + 2;
    }

    /**
     * The tooltip of the button under the mouse, or the entry's own over its name.
     */
    @Override
    public Optional<Component[]> getTooltip(int mouseX, int mouseY) {
        for (int i = 0; i < this.buttons.size(); i++) {
            var delay = this.delays.get(i);
            if (this.buttons.get(i).isMouseOver(mouseX, mouseY))
                return delay.ready() ? Optional.of(this.buttonTooltips.get(i)) : Optional.empty();
        }
        return this.nameDelay.ready() ? Optional.of(this.tooltip) : Optional.empty();
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return this.buttons;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return this.buttons;
    }
}
