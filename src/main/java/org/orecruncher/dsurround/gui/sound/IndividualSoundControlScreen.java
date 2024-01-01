package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.List;

public class IndividualSoundControlScreen extends Screen {

    private static final int TOP_OFFSET = 10;
    private static final int BOTTOM_OFFSET = 15;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 80;

    private static final int SEARCH_BAR_WIDTH = 200;
    private static final int SEARCH_BAR_HEIGHT = 20;

    private static final int SELECTION_HEIGHT_OFFSET = 5;
    private static final int SELECTION_WIDTH = 600;
    private static final int SELECTION_HEIGHT = 24;

    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 10;
    private static final int CONTROL_WIDTH = BUTTON_WIDTH * 2 + BUTTON_SPACING;

    private static final int TOOLTIP_Y_OFFSET = 30;

    private static final Component SAVE = Component.translatable("gui.done");
    private static final Component CANCEL = Component.translatable("gui.cancel");

    protected final Screen parent;
    protected final boolean enablePlay;
    protected EditBox searchField;
    protected IndividualSoundControlList soundConfigList;
    protected Button save;
    protected Button cancel;

    public IndividualSoundControlScreen(final Screen parent, final boolean enablePlay) {
        super(Component.translatable("dsurround.text.keybind.individualSoundConfig"));
        this.parent = parent;
        this.enablePlay = enablePlay;
    }

    @Override
    protected void init() {
        // Setup search bar
        final int searchBarLeftMargin = (this.width - SEARCH_BAR_WIDTH) / 2;
        final int searchBarY = TOP_OFFSET + HEADER_HEIGHT - SEARCH_BAR_HEIGHT;
        this.searchField = new EditBox(
                this.font,
                searchBarLeftMargin,
                searchBarY,
                SEARCH_BAR_WIDTH,
                SEARCH_BAR_HEIGHT,
                this.searchField,   // Copy existing data over
                Component.empty());

        this.searchField.setResponder((filter) -> this.soundConfigList.setSearchFilter(() -> filter));
        this.addWidget(this.searchField);

        // Set up the list control
        final int topY = TOP_OFFSET + HEADER_HEIGHT + SELECTION_HEIGHT_OFFSET;
        final int bottomY = this.height - BOTTOM_OFFSET - FOOTER_HEIGHT - SELECTION_HEIGHT_OFFSET;
        this.soundConfigList = new IndividualSoundControlList(
                this,
                GameUtils.getMC(),
                this.width,
                bottomY,
                topY,
                SELECTION_WIDTH,
                SELECTION_HEIGHT,
                this.enablePlay,
                () -> this.searchField.getValue(),
                this.soundConfigList);

        this.addWidget(this.soundConfigList);

        // Set the control buttons at the bottom
        final int controlMargin = (this.width - CONTROL_WIDTH) / 2;
        final int controlHeight = this.height - BOTTOM_OFFSET - BUTTON_HEIGHT;

        this.save = Button.builder(SAVE, this::save)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .pos(controlMargin, controlHeight)
                .build();
        this.addWidget(this.save);

        this.cancel = Button.builder(CANCEL, this::cancel)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .pos(controlMargin + BUTTON_WIDTH + BUTTON_SPACING, controlHeight)
                .build();
        this.addWidget(this.cancel);

        this.setFocused(this.searchField);
    }

    public void tick() {
        this.soundConfigList.tick();

        // Need to tick the Sound Manager because when the game is paused, sounds are not
        // processed.  We do this to enable handling of the "play" button.  (If the game
        // is not paused, mobs and things will still wander around and can cause a
        // problem for the player while their head is buried in the menu.)
        if (this.enablePlay)
            GameUtils.getSoundManager().tick(false);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers) || this.searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    public void closeScreen() {
        GameUtils.setScreen(this.parent);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return this.searchField.charTyped(codePoint, modifiers);
    }

    public void render(final GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        this.renderTransparentBackground(context);

        context.drawCenteredString(this.font, this.title, this.width / 2, TOP_OFFSET, ColorPalette.MC_WHITE.getValue());

        this.soundConfigList.render(context, mouseX, mouseY, partialTicks);
        this.searchField.render(context, mouseX, mouseY, partialTicks);
        this.save.render(context, mouseX, mouseY, partialTicks);
        this.cancel.render(context, mouseX, mouseY, partialTicks);

        if (this.soundConfigList.isMouseOver(mouseX, mouseY)) {
            final IndividualSoundControlListEntry entry = this.soundConfigList.getEntryAt(mouseX, mouseY);
            if (entry != null) {
                final List<FormattedCharSequence> toolTip = entry.getToolTip(mouseX, mouseY);
                context.renderTooltip(this.font, toolTip, mouseX, mouseY + TOOLTIP_Y_OFFSET);
            }
        }
    }

    // Handlers

    protected void save(final Button button) {
        // Gather the changes and push to underlying routine for parsing and packaging
        this.soundConfigList.saveChanges();
        this.onClose();
        this.closeScreen();
    }

    protected void cancel(final Button button) {
        // Just discard - no processing
        this.onClose();
        this.closeScreen();
    }
}