package org.orecruncher.dsurround.gui.sound;

import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;
import org.orecruncher.dsurround.lib.gui.TextWidget;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.*;

public class IndividualSoundControlListEntry extends ContainerObjectSelectionList.Entry<IndividualSoundControlListEntry> implements AutoCloseable {

    private static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    private static final IAudioPlayer AUDIO_PLAYER = ContainerManager.resolve(IAudioPlayer.class);
    private static final IPlatform PLATFORM = Library.PLATFORM;

    private static final int TOOLTIP_WIDTH = 300;

    private static final Style STYLE_MOD_NAME = Style.EMPTY.withColor(ColorPalette.GOLD);
    private static final Style STYLE_ID = Style.EMPTY.withColor(ColorPalette.SLATEGRAY);
    private static final Style STYLE_CATEGORY = Style.EMPTY.withColor(ColorPalette.FRESH_AIR);
    private static final Style STYLE_SUBTITLE = Style.EMPTY.withColor(ColorPalette.APRICOT).withItalic(true);
    private static final Style STYLE_CREDIT_NAME = Style.EMPTY.withColor(ColorPalette.GREEN);
    private static final Style STYLE_CREDIT_AUTHOR = Style.EMPTY.withColor(ColorPalette.WHITE);
    private static final Style STYLE_CREDIT_LICENSE = Style.EMPTY.withItalic(true).withColor(ColorPalette.MC_DARKAQUA);
    private static final Style STYLE_HELP = Style.EMPTY.withItalic(true).withColor(ColorPalette.KEY_LIME);

    private static final FormattedCharSequence VANILLA_CREDIT = Component.translatable("dsurround.text.soundconfig.vanilla").getVisualOrderText();
    private static final Collection<Component> VOLUME_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.volume.help", TOOLTIP_WIDTH, STYLE_HELP);
    private static final Collection<Component> PLAY_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.play.help", TOOLTIP_WIDTH, STYLE_HELP);
    private static final Collection<Component> CULL_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.cull.help", TOOLTIP_WIDTH, STYLE_HELP);
    private static final Collection<Component> BLOCK_HELP = GuiHelpers.getTrimmedTextCollection("dsurround.text.soundconfig.block.help", TOOLTIP_WIDTH, STYLE_HELP);
    private static final int CONTROL_SPACING = 3;

    private final IndividualSoundConfigEntry config;
    private final TextWidget label;
    private final VolumeSliderControl volume;
    private final BlockButton blockButton;
    private final CullButton cullButton;
    private final @Nullable SoundPlayButton playButton;

    private final List<AbstractWidget> children = new ArrayList<>();
    private final List<FormattedCharSequence> cachedToolTip = new ArrayList<>();

    private ConfigSoundInstance soundPlay;

    public IndividualSoundControlListEntry(final IndividualSoundConfigEntry data, final boolean enablePlay) {
        this.config = data;

        this.label = new TextWidget(0, 0, 200, GameUtils.getTextRenderer().lineHeight, Component.literal(data.soundEventId.toString()), GameUtils.getTextRenderer());
        this.children.add(this.label);

        this.volume = new VolumeSliderControl(this, 0, 0);
        this.children.add(this.volume);

        this.blockButton = new BlockButton(this.config.block, this::toggleBlock);
        this.children.add(this.blockButton);

        this.cullButton = new CullButton(this.config.cull, this::toggleCull);
        this.children.add(this.cullButton);

        if (enablePlay) {
            this.playButton = new SoundPlayButton(this::play);
            this.children.add(this.playButton);
        } else {
            this.playButton = null;
        }
    }

    public int getWidth() {
        int width = this.label.getWidth();
        width += this.cullButton.getWidth() + this.blockButton.getWidth() + this.volume.getWidth() + 4 * CONTROL_SPACING;
        if (this.playButton != null)
            width += this.playButton.getWidth() + CONTROL_SPACING;
        return width;
    }

    public void setWidth(int width) {
        var fixedWidth = this.cullButton.getWidth() + this.blockButton.getWidth() + this.volume.getWidth() + 4 * CONTROL_SPACING;
        if (this.playButton != null)
            fixedWidth += this.playButton.getWidth() + CONTROL_SPACING;
        width -= fixedWidth;
        if (width < 100)
            width = 100;
        this.label.setWidth(width);
    }

    public void mouseMoved(double mouseX, double mouseY) {
        AbstractWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            child.mouseMoved(mouseX, mouseY);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        // TODO:  What?
        return this.children;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        AbstractWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseClicked(mouseX, mouseY, button);
        return false;
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
        return ImmutableList.of();
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AbstractWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        AbstractWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        AbstractWidget child = this.findChild(mouseX, mouseY);
        if (child != null)
            return child.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        return false;
    }

    private AbstractWidget findChild(double mouseX, double mouseY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            for (AbstractWidget e : this.children) {
                if (e.isMouseOver(mouseX, mouseY)) {
                    return e;
                }
            }
        }
        return null;
    }

    @Override
    public void render(final @NotNull GuiGraphics context, int index, int rowTop, int rowLeft, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean mouseOver, float partialTick_) {
        final var font = GameUtils.getTextRenderer();
        final int labelY = rowTop + (rowHeight - font.lineHeight) / 2;

        this.label.setX(rowLeft);
        this.label.setY(labelY);

        // Need to position the other controls appropriately
        int rightMargin = rowLeft + rowWidth;
        this.volume.setX(rightMargin - this.volume.getWidth());
        this.volume.setY(rowTop);
        this.volume.setHeight(rowHeight);
        rightMargin -= this.volume.getWidth() + CONTROL_SPACING;

        if (this.playButton != null) {
            this.playButton.setX(rightMargin - this.playButton.getWidth());
            this.playButton.setY(rowTop);
            this.playButton.setHeight(rowHeight);
            rightMargin -= this.playButton.getWidth() + CONTROL_SPACING;
        }

        this.blockButton.setX(rightMargin - this.blockButton.getWidth());
        this.blockButton.setY(rowTop);
        this.blockButton.setHeight(rowHeight);
        rightMargin -= this.blockButton.getWidth() + CONTROL_SPACING;

        this.cullButton.setX(rightMargin - this.cullButton.getWidth());
        this.cullButton.setHeight(rowHeight);
        this.cullButton.setY(rowTop);

        for (final AbstractWidget w : this.children)
            w.render(context, mouseX, mouseY, partialTick_);
    }

    protected void toggleBlock(Button button) {
        if (button instanceof BlockButton bb) {
            this.config.block = bb.toggle();
        }
    }

    protected void toggleCull(Button button) {
        if (button instanceof CullButton cb)
            this.config.cull = cb.toggle();
    }

    protected void play(final Button button) {
        if (button instanceof SoundPlayButton sp) {
            if (this.soundPlay == null) {
                this.soundPlay = this.playSound(this.config);
                sp.play();
            } else {
                AUDIO_PLAYER.stop(this.soundPlay);
                this.soundPlay = null;
                sp.stop();
            }
        }
    }

    protected ConfigSoundInstance playSound(IndividualSoundConfigEntry entry) {
        var metadata = SOUND_LIBRARY.getSoundMetadata(entry.soundEventId);
        ConfigSoundInstance sound = ConfigSoundInstance.create(entry.soundEventId, metadata.getCategory(), () -> entry.volumeScale / 100F);
        AUDIO_PLAYER.play(sound);
        return sound;
    }

    @Override
    public void close() {
        if (this.soundPlay != null) {
            AUDIO_PLAYER.stop(this.soundPlay);
            this.soundPlay = null;
        }
    }

    public void tick() {
        if (this.soundPlay != null && this.playButton != null) {
            if (!AUDIO_PLAYER.isPlaying(this.soundPlay)) {
                this.soundPlay = null;
                this.playButton.stop();
            }
        }
    }

    protected List<FormattedCharSequence> getToolTip(final int mouseX, final int mouseY) {
        // Cache the static part of the tooltip if needed
        if (this.cachedToolTip.isEmpty()) {
            ResourceLocation id = this.config.soundEventId;
            this.resolveDisplayName(id.getNamespace())
                    .ifPresent(name -> {
                        FormattedCharSequence modName = FormattedCharSequence.forward(Objects.requireNonNull(ChatFormatting.stripFormatting(name)), STYLE_MOD_NAME);
                        this.cachedToolTip.add(modName);
                    });

            @SuppressWarnings("ConstantConditions")
            FormattedCharSequence soundLocationId = FormattedCharSequence.forward(id.toString(), STYLE_ID);

            this.cachedToolTip.add(soundLocationId);

            SoundMetadata metadata = SOUND_LIBRARY.getSoundMetadata(id);
            if (metadata != null) {
                if (!metadata.getTitle().equals(Component.empty()))
                    this.cachedToolTip.add(metadata.getTitle().getVisualOrderText());

                this.cachedToolTip.add(Component.literal(metadata.getCategory().toString()).withStyle(STYLE_CATEGORY).getVisualOrderText());

                if (!metadata.getSubTitle().equals(Component.empty())) {
                    this.cachedToolTip.add(metadata.getSubTitle().copy().withStyle(STYLE_SUBTITLE).getVisualOrderText());
                }

                if (!metadata.getCredits().isEmpty()) {
                    for (var credit : metadata.getCredits()) {
                        this.cachedToolTip.add(Component.empty().getVisualOrderText());
                        this.cachedToolTip.add(credit.name().copy().withStyle(STYLE_CREDIT_NAME).getVisualOrderText());
                        this.cachedToolTip.add(credit.author().copy().withStyle(STYLE_CREDIT_AUTHOR).getVisualOrderText());
                        if (credit.webSite().isPresent()) {
                            this.cachedToolTip.add(credit.webSite().get().copy().withStyle(STYLE_CREDIT_AUTHOR).getVisualOrderText());
                        }
                        this.cachedToolTip.add(credit.license().copy().withStyle(STYLE_CREDIT_LICENSE).getVisualOrderText());
                    }
                }
            }

            if (id.getNamespace().equals("minecraft")) {
                this.cachedToolTip.add(VANILLA_CREDIT);
            }
        }

        List<FormattedCharSequence> generatedTip = new ArrayList<>(this.cachedToolTip);

        Collection<Component> toAppend = null;
        if (this.volume.isMouseOver(mouseX, mouseY)) {
            toAppend = VOLUME_HELP;
        } else if (this.blockButton.isMouseOver(mouseX, mouseY)) {
            toAppend = BLOCK_HELP;
        } else if (this.cullButton.isMouseOver(mouseX, mouseY)) {
            toAppend = CULL_HELP;
        } else if (this.playButton != null && this.playButton.isMouseOver(mouseX, mouseY)) {
            toAppend = PLAY_HELP;
        }

        if (toAppend != null) {
            generatedTip.add(FormattedCharSequence.EMPTY);
            toAppend.forEach(e -> generatedTip.add(e.getVisualOrderText()));
        }

        return generatedTip;
    }

    private Optional<String> resolveDisplayName(String namespace) {
        var displayName = PLATFORM.getModDisplayName(namespace);
        if (displayName.isPresent())
            return displayName;

        // Could be a resource pack
        return GameUtils.getResourceManager().listPacks()
                .filter(pack -> pack.getNamespaces(PackType.CLIENT_RESOURCES).contains(namespace))
                .map(PackResources::packId)
                .findAny();
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