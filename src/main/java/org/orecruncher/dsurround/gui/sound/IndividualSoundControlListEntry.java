package org.orecruncher.dsurround.gui.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.SoundLibrary;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Environment(EnvType.CLIENT)
public class IndividualSoundControlListEntry extends EntryListWidget.Entry<IndividualSoundControlListEntry> implements AutoCloseable {

    private static final int BUTTON_WIDTH = 60;
    private static final int TOOLTIP_WIDTH = 300;
    private static final Text CULL_ON = Text.translatable("dsurround.text.soundconfig.cull");
    private static final Text CULL_OFF = Text.translatable("dsurround.text.soundconfig.nocull");
    private static final Text BLOCK_ON = Text.translatable("dsurround.text.soundconfig.block");
    private static final Text BLOCK_OFF = Text.translatable("dsurround.text.soundconfig.noblock");
    private static final Text PLAY = Text.translatable("dsurround.text.soundconfig.play");
    private static final Text STOP = Text.translatable("dsurround.text.soundconfig.stop");
    private static final OrderedText VANILLA_CREDIT = Text.translatable("dsurround.text.soundconfig.vanilla").asOrderedText();

    private static final Formatting[] CODING = new Formatting[]{Formatting.ITALIC, Formatting.AQUA};
    private static final Collection<OrderedText> VOLUME_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.volume.help", TOOLTIP_WIDTH, CODING);
    private static final Collection<OrderedText> PLAY_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.play.help", TOOLTIP_WIDTH, CODING);
    private static final Collection<OrderedText> CULL_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.cull.help", TOOLTIP_WIDTH, CODING);
    private static final Collection<OrderedText> BLOCK_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.block.help", TOOLTIP_WIDTH, CODING);

    private static final Style modNameStyle = Style.EMPTY.withFormatting(Formatting.GOLD);
    private static final Style idStyle = Style.EMPTY.withFormatting(Formatting.GRAY);

    private static final int CONTROL_SPACING = 3;

    private final IndividualSoundConfigEntry config;
    private final VolumeSliderControl volume;
    private final ButtonWidget blockButton;
    private final ButtonWidget cullButton;
    private final ButtonWidget playButton;

    private final List<ClickableWidget> children = new ArrayList<>();
    private final List<OrderedText> cachedToolTip = new ArrayList<>();

    private ConfigSoundInstance soundPlay;

    public IndividualSoundControlListEntry(final IndividualSoundConfigEntry data, final boolean enablePlay) {
        this.config = data;
        this.volume = new VolumeSliderControl(this, 0, 0);
        this.children.add(this.volume);

        this.blockButton = ButtonWidget.builder(this.config.block ? BLOCK_ON : BLOCK_OFF, this::toggleBlock)
            .size(BUTTON_WIDTH, 0)
            .build();
        this.children.add(this.blockButton);

        this.cullButton = ButtonWidget.builder(this.config.block ? CULL_ON : CULL_OFF, this::toggleCull)
            .size(BUTTON_WIDTH, 0)
            .build();
        this.children.add(this.cullButton);

        this.playButton = ButtonWidget.builder(PLAY, this::play)
            .size(BUTTON_WIDTH, 0)
            .build();

        this.playButton.active = enablePlay;
        this.children.add(this.playButton);
    }

    public void mouseMoved(double mouseX, double mouseY) {
        ClickableWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            child.mouseMoved(mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ClickableWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseClicked(mouseX, mouseY, button);
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ClickableWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        ClickableWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        ClickableWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        return false;
    }

    private ClickableWidget findChild(double mouseX, double mouseY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            for (ClickableWidget e : this.children) {
                if (e.isMouseOver(mouseX, mouseY)) {
                    return e;
                }
            }
        }
        return null;
    }

    @Override
    public void render(final DrawContext context, int index, int rowTop, int rowLeft, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean mouseOver, float partialTick_) {
        final TextRenderer font = GameUtils.getTextRenderer();
        final int labelY = rowTop + (rowHeight - font.fontHeight) / 2;
        final String text = this.config.soundEventId.toString();
        context.drawText(font, text, rowLeft, labelY, ColorPalette.WHITE.getRGB(), false);

        // Need to position the other controls appropriately
        int rightMargin = rowLeft + rowWidth;
        this.volume.setX(rightMargin - this.volume.getWidth());
        this.volume.setY(rowTop);
        this.volume.setHeight(rowHeight);
        rightMargin -= this.volume.getWidth() + CONTROL_SPACING;

        this.playButton.setX(rightMargin - this.playButton.getWidth());
        this.playButton.setY(rowTop);
        this.playButton.setHeight(rowHeight);
        rightMargin -= this.playButton.getWidth() + CONTROL_SPACING;

        this.blockButton.setX(rightMargin - this.blockButton.getWidth());
        this.blockButton.setY(rowTop);
        this.blockButton.setHeight(rowHeight);
        rightMargin -= this.blockButton.getWidth() + CONTROL_SPACING;

        this.cullButton.setX(rightMargin - this.cullButton.getWidth());
        this.cullButton.setHeight(rowHeight);
        this.cullButton.setY(rowTop);

        for (final ClickableWidget w : this.children)
            w.render(context, mouseX, mouseY, partialTick_);
    }

    protected void toggleBlock(final ButtonWidget button) {
        this.config.block = !this.config.block;
        button.setMessage(this.config.block ? BLOCK_ON : BLOCK_OFF);
    }

    protected void toggleCull(final ButtonWidget button) {
        this.config.cull = !this.config.cull;
        button.setMessage(this.config.cull ? CULL_ON : CULL_OFF);
    }

    protected void play(final ButtonWidget button) {
        if (this.soundPlay == null) {
            this.soundPlay = SoundLibraryHelpers.playSound(this.config);
            button.setMessage(STOP);
        } else {
            SoundLibraryHelpers.stopSound(this.soundPlay);
            this.soundPlay = null;
            button.setMessage(PLAY);
        }
    }

    @Override
    public void close() {
        if (this.soundPlay != null) {
            SoundLibraryHelpers.stopSound(this.soundPlay);
            this.soundPlay = null;
        }
    }

    public void tick() {
        if (this.soundPlay != null) {
            if (!SoundLibraryHelpers.isPlaying(this.soundPlay)) {
                this.soundPlay = null;
                this.playButton.setMessage(PLAY);
            }
        }
    }

    protected List<OrderedText> getToolTip(final int mouseX, final int mouseY) {

        // Cache the static part of the tooltip if needed
        if (this.cachedToolTip.isEmpty()) {

            Identifier id = this.config.soundEventId;
            final String mod = FrameworkUtils.getModDisplayName(id.getNamespace());
            assert mod != null;
            @SuppressWarnings("ConstantConditions")
            OrderedText modName = OrderedText.styledForwardsVisitedString(Formatting.strip(mod), modNameStyle);
            OrderedText soundLocationId = OrderedText.styledForwardsVisitedString(this.config.soundEventId.toString(), idStyle);

            this.cachedToolTip.add(modName);
            this.cachedToolTip.add(soundLocationId);

            SoundMetadata metadata = SoundLibrary.getSoundMetadata(id);
            if (metadata != null) {
                if (!metadata.getTitle().equals(Text.empty()))
                    this.cachedToolTip.add(metadata.getTitle().asOrderedText());
                if (!metadata.getCaption().equals(Text.empty()))
                    this.cachedToolTip.add(Text.translatable("dsurround.text.soundconfig.caption", metadata.getCaption()).asOrderedText());

                for (Text t : metadata.getCredits())
                    this.cachedToolTip.add(t.asOrderedText());
            }

            if (id.getNamespace().equals("minecraft")) {
                this.cachedToolTip.add(VANILLA_CREDIT);
            }
        }

        List<OrderedText> generatedTip = new ArrayList<>(this.cachedToolTip);

        Collection<OrderedText> toAppend = null;
        if (this.volume.isMouseOver(mouseX, mouseY)) {
            toAppend = VOLUME_HELP;
        } else if (this.blockButton.isMouseOver(mouseX, mouseY)) {
            toAppend = BLOCK_HELP;
        } else if (this.cullButton.isMouseOver(mouseX, mouseY)) {
            toAppend = CULL_HELP;
        } else if (this.playButton.isMouseOver(mouseX, mouseY)) {
            toAppend = PLAY_HELP;
        }

        if (toAppend != null) {
            generatedTip.add(OrderedText.EMPTY);
            generatedTip.addAll(toAppend);
        }

        return generatedTip;
    }

    /**
     * Retrieves the updated data from the entry
     *
     * @return Updated IndividualSoundControl data
     */
    public IndividualSoundConfigEntry getData() {
        return this.config;
    }

}