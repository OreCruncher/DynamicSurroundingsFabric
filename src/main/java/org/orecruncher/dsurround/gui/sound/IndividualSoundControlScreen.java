package org.orecruncher.dsurround.gui.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.util.List;

@Environment(EnvType.CLIENT)
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

    private static final Text SAVE = Text.translatable("gui.done");
    private static final Text CANCEL = Text.translatable("gui.cancel");

    protected final Screen parent;
    protected final boolean enablePlay;
    protected TextFieldWidget searchField;
    protected IndividualSoundControlList soundConfigList;
    protected ButtonWidget save;
    protected ButtonWidget cancel;

    public IndividualSoundControlScreen(final Screen parent, final boolean enablePlay) {
        super(Text.translatable("dsurround.text.keybind.individualSoundConfig"));
        this.parent = parent;
        this.enablePlay = enablePlay;
    }

    @Override
    protected void init() {
        // Setup search bar
        final int searchBarLeftMargin = (this.width - SEARCH_BAR_WIDTH) / 2;
        final int searchBarY = TOP_OFFSET + HEADER_HEIGHT - SEARCH_BAR_HEIGHT;
        this.searchField = new TextFieldWidget(
                this.textRenderer,
                searchBarLeftMargin,
                searchBarY,
                SEARCH_BAR_WIDTH,
                SEARCH_BAR_HEIGHT,
                this.searchField,   // Copy existing data over
                Text.empty());

        this.searchField.setChangedListener((filter) -> this.soundConfigList.setSearchFilter(() -> filter, false));
        this.addSelectableChild(this.searchField);

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
                () -> this.searchField.getText(),
                this.soundConfigList);

        this.addSelectableChild(this.soundConfigList);

        // Set the control buttons at the bottom
        final int controlMargin = (this.width - CONTROL_WIDTH) / 2;
        final int controlHeight = this.height - BOTTOM_OFFSET - BUTTON_HEIGHT;

        this.save = ButtonWidget.builder(SAVE, this::save)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .position(controlMargin, controlHeight)
                .build();
        this.addSelectableChild(this.save);

        this.cancel = ButtonWidget.builder(CANCEL, this::cancel)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .position(controlMargin + BUTTON_WIDTH + BUTTON_SPACING, controlHeight)
                .build();
        this.addSelectableChild(this.cancel);

        this.setFocused(this.searchField);
    }

    public void tick() {
        //this.searchField.tick();
        this.soundConfigList.tick();

        // Need to tick the Sound Manager because when the game is paused sounds are not
        // processed.  We do this to enable handling of the "play" button.  (If the game
        // were not paused mobs and things will still wander around and can cause a
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

    public void render(final DrawContext context, int mouseX, int mouseY, float partialTicks) {
        this.renderInGameBackground(context);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TOP_OFFSET, ColorPalette.MC_WHITE.getRGB());

        this.soundConfigList.render(context, mouseX, mouseY, partialTicks);
        this.searchField.render(context, mouseX, mouseY, partialTicks);
        this.save.render(context, mouseX, mouseY, partialTicks);
        this.cancel.render(context, mouseX, mouseY, partialTicks);

        if (this.soundConfigList.isMouseOver(mouseX, mouseY)) {
            final IndividualSoundControlListEntry entry = this.soundConfigList.getEntryAt(mouseX, mouseY);
            if (entry != null) {
                final List<OrderedText> toolTip = entry.getToolTip(mouseX, mouseY);
                context.drawOrderedTooltip(this.textRenderer, toolTip, mouseX, mouseY + TOOLTIP_Y_OFFSET);
            }
        }
    }

    // Handlers

    protected void save(final ButtonWidget button) {
        // Gather the changes and push to underlying routine for parsing and packaging
        this.soundConfigList.saveChanges();
        this.close();
        this.closeScreen();
    }

    protected void cancel(final ButtonWidget button) {
        // Just discard - no processing
        this.close();
        this.closeScreen();
    }
}