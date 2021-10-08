package org.orecruncher.dsurround.gui.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ButtonControl;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;
import org.orecruncher.dsurround.config.SoundLibrary;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Environment(EnvType.CLIENT)
public class IndividualSoundControlListEntry extends EntryListWidget.Entry<IndividualSoundControlListEntry> implements AutoCloseable {

    private static final int BUTTON_WIDTH = 60;
    private static final int TOOLTIP_WIDTH = 300;
    private static final Text CULL_ON = new TranslatableText("dsurround.text.soundconfig.cull");
    private static final Text CULL_OFF = new TranslatableText("dsurround.text.soundconfig.nocull");
    private static final Text BLOCK_ON = new TranslatableText("dsurround.text.soundconfig.block");
    private static final Text BLOCK_OFF = new TranslatableText("dsurround.text.soundconfig.noblock");
    private static final Text PLAY = new TranslatableText("dsurround.text.soundconfig.play");
    private static final Text STOP = new TranslatableText("dsurround.text.soundconfig.stop");
    private static final OrderedText VANILLA_CREDIT = new TranslatableText("dsurround.text.soundconfig.vanilla").asOrderedText();

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
    private final ButtonControl blockButton;
    private final ButtonControl cullButton;
    private final ButtonControl playButton;

    private final List<ClickableWidget> children = new ArrayList<>();
    private final List<OrderedText> cachedToolTip = new ArrayList<>();

    private ConfigSoundInstance soundPlay;

    public IndividualSoundControlListEntry(final IndividualSoundConfigEntry data, final boolean enablePlay) {
        this.config = data;
        this.volume = new VolumeSliderControl(this, 0, 0);
        this.children.add(this.volume);

        this.blockButton = new ButtonControl(
                0,
                0,
                BUTTON_WIDTH,
                0,
                this.config.block ? BLOCK_ON : BLOCK_OFF,
                this::toggleBlock);
        this.children.add(this.blockButton);

        this.cullButton = new ButtonControl(
                0,
                0,
                BUTTON_WIDTH,
                0,
                this.config.cull ? CULL_ON : CULL_OFF,
                this::toggleCull);
        this.children.add(this.cullButton);

        this.playButton = new ButtonControl(
                0,
                0,
                BUTTON_WIDTH,
                0,
                PLAY,
                this::play) {

            @Override
            public void playDownSound(final SoundManager ignore) {
                // Suppress the button click to avoid conflicting with the sound play
            }
        };

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

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        ClickableWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseScrolled(mouseX, mouseY, amount);
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
    public void render(final MatrixStack matrixStack, int index, int rowTop, int rowLeft, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean mouseOver, float partialTick_) {
        final TextRenderer font = GameUtils.getTextRenderer();
        final float labelY = rowTop + (rowHeight - font.fontHeight) / 2F;
        final String text = this.config.id;
        font.draw(matrixStack, text, (float) rowLeft, labelY, ColorPalette.WHITE.getRGB());

        // Need to position the other controls appropriately
        int rightMargin = rowLeft + rowWidth;
        this.volume.x = rightMargin - this.volume.getWidth();
        this.volume.y = rowTop;
        this.volume.setHeight(rowHeight);
        rightMargin -= this.volume.getWidth() + CONTROL_SPACING;

        this.playButton.x = rightMargin - this.playButton.getWidth();
        this.playButton.y = rowTop;
        this.playButton.setHeight(rowHeight);
        rightMargin -= this.playButton.getWidth() + CONTROL_SPACING;

        this.blockButton.x = rightMargin - this.blockButton.getWidth();
        this.blockButton.y = rowTop;
        this.blockButton.setHeight(rowHeight);
        rightMargin -= this.blockButton.getWidth() + CONTROL_SPACING;

        this.cullButton.x = rightMargin - this.cullButton.getWidth();
        this.cullButton.setHeight(rowHeight);
        this.cullButton.y = rowTop;

        for (final ClickableWidget w : this.children)
            w.render(matrixStack, mouseX, mouseY, partialTick_);
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
        if (this.cachedToolTip.size() == 0) {

            Identifier id = new Identifier(this.config.id);
            final String mod = FrameworkUtils.getModDisplayName(id.getNamespace());
            assert mod != null;
            OrderedText modName = OrderedText.styledForwardsVisitedString(mod, modNameStyle);
            OrderedText soundLocationId = OrderedText.styledForwardsVisitedString(this.config.id, idStyle);

            this.cachedToolTip.add(modName);
            this.cachedToolTip.add(soundLocationId);

            SoundMetadata metadata = SoundLibrary.getSoundMetadata(id);
            if (metadata != null) {
                if (metadata.getTitle() != LiteralText.EMPTY)
                    this.cachedToolTip.add(metadata.getTitle().asOrderedText());
                if (metadata.getCaption() != LiteralText.EMPTY)
                    this.cachedToolTip.add(new TranslatableText("dsurround.text.soundconfig.caption", metadata.getCaption()).asOrderedText());

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