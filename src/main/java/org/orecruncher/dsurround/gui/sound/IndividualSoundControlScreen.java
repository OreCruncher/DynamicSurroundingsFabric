package org.orecruncher.dsurround.gui.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.List;

@Environment(EnvType.CLIENT)
public class IndividualSoundControlScreen extends Screen {

    private static final int TOP_OFFSET = 10;
    private static final int BOTTOM_OFFSET = 15;
    private static final int HEADER_HEIGHT = 35;
    private static final int FOOTER_HEIGHT = 50;

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

    private static final Text SAVE = ScreenTexts.DONE;
    private static final Text CANCEL = ScreenTexts.CANCEL;

    protected final Screen parent;
    protected final boolean enablePlay;
    protected TextFieldWidget searchField;
    protected IndividualSoundControlList soundConfigList;
    protected ButtonWidget save;
    protected ButtonWidget cancel;

    public IndividualSoundControlScreen(final Screen parent, final boolean enablePlay) {
        super(new TranslatableText("dsurround.text.keybind.individualSoundConfig"));
        this.parent = parent;
        this.enablePlay = enablePlay;
    }

    @Override
    protected void init() {
        GameUtils.getMC().keyboard.setRepeatEvents(true);

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
                LiteralText.EMPTY);

        this.searchField.setChangedListener((filter) -> this.soundConfigList.setSearchFilter(() -> filter, false));
        this.addSelectableChild(this.searchField);

        // Setup the list control
        final int topY = TOP_OFFSET + HEADER_HEIGHT + SELECTION_HEIGHT_OFFSET;
        final int bottomY = this.height - BOTTOM_OFFSET - FOOTER_HEIGHT - SELECTION_HEIGHT_OFFSET;
        this.soundConfigList = new IndividualSoundControlList(
                this,
                GameUtils.getMC(),
                this.width,
                this.height,
                topY,
                bottomY,
                SELECTION_WIDTH,
                SELECTION_HEIGHT,
                this.enablePlay,
                () -> this.searchField.getText(),
                this.soundConfigList);

        this.addSelectableChild(this.soundConfigList);

        // Set the control buttons at the bottom
        final int controlMargin = (this.width - CONTROL_WIDTH) / 2;
        final int controlHeight = this.height - BOTTOM_OFFSET - BUTTON_HEIGHT;
        this.save = new ButtonWidget(
                controlMargin,
                controlHeight,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                SAVE,
                this::save);
        this.addDrawableChild(this.save);

        this.cancel = new ButtonWidget(
                controlMargin + BUTTON_WIDTH + BUTTON_SPACING,
                controlHeight,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                CANCEL,
                this::cancel);
        this.addDrawableChild(this.cancel);

        this.setFocused(this.searchField);
    }

    public void tick() {
        this.searchField.tick();
        this.soundConfigList.tick();

        // Need to tick the Sound Manager because when the game is paused sounds are not
        // processed.  We do this to enable handling of the "play" button.
        GameUtils.getSoundHander().tick(false);
    }

    public boolean isPauseScreen() {
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers) || this.searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    public void closeScreen() {
        GameUtils.getMC().setScreen(this.parent);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return this.searchField.charTyped(codePoint, modifiers);
    }

    public void render(final MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        this.soundConfigList.render(matrixStack, mouseX, mouseY, partialTicks);
        this.searchField.render(matrixStack, mouseX, mouseY, partialTicks);
        DrawableHelper.drawCenteredText(matrixStack, this.textRenderer, this.title, this.width / 2, TOP_OFFSET, 16777215);
        super.render(matrixStack, mouseX, mouseY, partialTicks);

        if (this.soundConfigList.isMouseOver(mouseX, mouseY)) {
            final IndividualSoundControlListEntry entry = this.soundConfigList.getEntryAt(mouseX, mouseY);
            if (entry != null) {
                final List<OrderedText> toolTip = entry.getToolTip(mouseX, mouseY);
                this.renderOrderedTooltip(matrixStack, toolTip, mouseX, mouseY + TOOLTIP_Y_OFFSET);
            }
        }
    }

    // Handlers

    protected void save(final ButtonWidget button) {
        // Gather the changes and push to underlying routine for parsing and packaging
        this.soundConfigList.saveChanges();
        this.onClose();
        this.closeScreen();
    }

    protected void cancel(final ButtonWidget button) {
        // Just discard - no processing
        this.onClose();
        this.closeScreen();
    }
}