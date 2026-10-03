package org.orecruncher.dsurround.gui.sound;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.IndividualSoundConfigEntry;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.GuiHelpers;
import org.orecruncher.dsurround.lib.gui.TextWidget;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.SoundMetadata;

import java.util.*;

/**
 * One row of the individual sound configuration list: the sound's id, its state (default, cull or block), an
 * optional play button, and its volume.
 */
public class IndividualSoundControlListEntry extends ContainerObjectSelectionList.Entry<IndividualSoundControlListEntry> implements AutoCloseable {

    private static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    private static final IAudioPlayer AUDIO_PLAYER = ContainerManager.resolve(IAudioPlayer.class);

    private static final int TOOLTIP_WIDTH = 300;

    private static final Style STYLE_MOD_NAME = Style.EMPTY.withColor(ColorPalette.GOLD);
    private static final Style STYLE_ID = Style.EMPTY.withColor(ColorPalette.SLATEGRAY);
    private static final Style STYLE_CATEGORY = Style.EMPTY.withColor(ColorPalette.FRESH_AIR);
    private static final Style STYLE_SUBTITLE = Style.EMPTY.withColor(ColorPalette.APRICOT).withItalic(true);
    private static final Style STYLE_CREDIT_NAME = Style.EMPTY.withColor(ColorPalette.GREEN);
    private static final Style STYLE_CREDIT_AUTHOR = Style.EMPTY.withColor(ColorPalette.WHITE);
    private static final Style STYLE_CREDIT_LICENSE = Style.EMPTY.withItalic(true).withColor(ColorPalette.MC_DARKAQUA);
    private static final Style STYLE_HELP = Style.EMPTY.withItalic(true).withColor(ColorPalette.KEY_LIME);

    // Translation keys rather than prepared text, so a language change in game is picked up
    private static final String VANILLA_CREDIT_KEY = "dsurround.text.soundconfig.vanilla";
    private static final String VOLUME_HELP_KEY = "dsurround.text.soundconfig.volume.help";
    private static final String PLAY_HELP_KEY = "dsurround.text.soundconfig.play.help";

    private static final Component SOUND_PLAY = Component.translatable("dsurround.text.soundconfig.play").withStyle(Style.EMPTY.withColor(ColorPalette.ELECTRIC_GREEN));
    private static final Component SOUND_STOP = Component.translatable("dsurround.text.soundconfig.stop").withStyle(Style.EMPTY.withColor(ColorPalette.RED).withBold(true));

    private static final int CONTROL_SPACING = 3;
    // Extra space between the label and the first control, on top of CONTROL_SPACING
    private static final int LABEL_GAP = 2 * CONTROL_SPACING;
    private static final int MIN_LABEL_WIDTH = 100;
    private static final int CONTROL_HEIGHT = 20;
    // Room either side of a button's text
    private static final int BUTTON_TEXT_PADDING = CONTROL_SPACING * 5;

    /**
     * What happens to a sound. A sound is never both culled and blocked; if a configuration says so, cull wins.
     */
    private enum SoundState {
        DEFAULT("dsurround.text.soundconfig.default", Style.EMPTY.withColor(ColorPalette.LGRAY), false, false),
        CULL("dsurround.text.soundconfig.cull", Style.EMPTY.withColor(ColorPalette.PUMPKIN_ORANGE), true, false),
        BLOCK("dsurround.text.soundconfig.block", Style.EMPTY.withColor(ColorPalette.RED).withBold(true), false, true);

        private final Component label;
        private final String helpKey;
        private final boolean cull;
        private final boolean block;

        SoundState(String key, Style style, boolean cull, boolean block) {
            this.label = Component.translatable(key).withStyle(style);
            this.helpKey = key + ".help";
            this.cull = cull;
            this.block = block;
        }

        Component label() {
            return this.label;
        }

        static SoundState from(IndividualSoundConfigEntry config) {
            if (config.cull)
                return CULL;
            return config.block ? BLOCK : DEFAULT;
        }

        void applyTo(IndividualSoundConfigEntry config) {
            config.cull = this.cull;
            config.block = this.block;
        }
    }

    private final IndividualSoundConfigEntry config;
    private final TextWidget label;
    private final CycleButton<SoundState> stateButton;
    private final @Nullable CycleButton<Boolean> playButton;
    private final VolumeSliderControl volume;

    // label | state | play | volume. Controls keep their natural height (as in vanilla lists) and sit at the top of
    // the row; the label is centered against them. Only the label's width changes.
    private final LinearLayout row = LinearLayout.horizontal().spacing(CONTROL_SPACING);
    private final int controlsWidth;

    // In visual order, which is also the keyboard navigation order
    private final List<AbstractWidget> children = new ArrayList<>();
    private final List<FormattedCharSequence> cachedToolTip = new ArrayList<>();

    private @Nullable ConfigSoundInstance soundPlay;

    public IndividualSoundControlListEntry(final IndividualSoundConfigEntry data, final boolean enablePlay) {
        this.config = data;

        var font = GameUtils.getTextRenderer();
        this.label = new TextWidget(0, 0, MIN_LABEL_WIDTH, font.lineHeight, Component.literal(data.soundEventId.toString()), font);

        int stateWidth = Arrays.stream(SoundState.values()).mapToInt(s -> font.width(s.label())).max().orElse(0) + BUTTON_TEXT_PADDING;
        this.stateButton = CycleButton.builder(SoundState::label)
                .withValues(SoundState.values())
                .withInitialValue(SoundState.from(this.config))
                .displayOnlyValue()
                .create(0, 0, stateWidth, CONTROL_HEIGHT, Component.empty(), (button, state) -> state.applyTo(this.config));

        if (enablePlay) {
            int playWidth = Math.max(font.width(SOUND_STOP), font.width(SOUND_PLAY)) + BUTTON_TEXT_PADDING;
            this.playButton = CycleButton.booleanBuilder(SOUND_STOP, SOUND_PLAY)
                    .withInitialValue(false)
                    .displayOnlyValue()
                    .create(0, 0, playWidth, CONTROL_HEIGHT, Component.empty(), (button, play) -> this.setPlaying(play));
        } else {
            this.playButton = null;
        }

        this.volume = new VolumeSliderControl(this.config);

        this.row.defaultCellSetting().alignVerticallyMiddle();
        this.row.addChild(this.label, s -> s.paddingRight(LABEL_GAP));
        this.row.addChild(this.stateButton);
        if (this.playButton != null)
            this.row.addChild(this.playButton);
        this.row.addChild(this.volume);
        this.row.visitWidgets(this.children::add);

        this.row.arrangeElements();
        this.controlsWidth = this.row.getWidth() - this.label.getWidth();
    }

    /**
     * The narrowest this row can be without its controls running past the right edge.
     */
    public int getMinimumWidth() {
        return MIN_LABEL_WIDTH + this.controlsWidth;
    }

    /**
     * Fits the row to the given width by stretching or shrinking the label, which never gets narrower than
     * {@link #MIN_LABEL_WIDTH}.
     */
    public void setWidth(int width) {
        this.label.setWidth(Math.max(MIN_LABEL_WIDTH, width - this.controlsWidth));
        this.row.arrangeElements();
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        return this.children;
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
        return this.children;
    }

    @Override
    public void render(final @NotNull GuiGraphics context, int index, int rowTop, int rowLeft, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean mouseOver, float partialTick) {
        // Moving the layout moves its widgets; they were arranged when the width was set
        this.row.setPosition(rowLeft, rowTop);
        for (final AbstractWidget w : this.children)
            w.render(context, mouseX, mouseY, partialTick);
    }

    /**
     * The data this row edits.
     */
    public IndividualSoundConfigEntry getData() {
        return this.config;
    }

    // ---- Playing the sound -----------------------------------------------------------------------------------

    private void setPlaying(boolean play) {
        this.stopSound();
        if (play) {
            var metadata = SOUND_LIBRARY.getSoundMetadata(this.config.soundEventId);
            // The volume is read live, so moving the slider changes a sound that is already playing
            this.soundPlay = ConfigSoundInstance.create(this.config.soundEventId, metadata.getCategory(), () -> this.config.volumeScale / 100F);
            AUDIO_PLAYER.play(this.soundPlay);
        }
    }

    private void stopSound() {
        if (this.soundPlay != null) {
            AUDIO_PLAYER.stop(this.soundPlay);
            this.soundPlay = null;
        }
    }

    /**
     * Resets the play button once the sound has finished on its own.
     */
    public void tick() {
        if (this.soundPlay != null && this.playButton != null && !AUDIO_PLAYER.isPlaying(this.soundPlay)) {
            this.soundPlay = null;
            this.playButton.setValue(false);
        }
    }

    @Override
    public void close() {
        this.stopSound();
    }

    // ---- Tooltip ---------------------------------------------------------------------------------------------

    /**
     * Information about the sound, followed by help for the control under the mouse, if any.
     */
    protected List<FormattedCharSequence> getToolTip(final int mouseX, final int mouseY) {
        if (this.cachedToolTip.isEmpty()) {
            this.buildSoundInfo(this.cachedToolTip);
        }

        List<FormattedCharSequence> generatedTip = new ArrayList<>(this.cachedToolTip);

        String helpKey = null;
        if (this.volume.isMouseOver(mouseX, mouseY)) {
            helpKey = VOLUME_HELP_KEY;
        } else if (this.stateButton.isMouseOver(mouseX, mouseY)) {
            helpKey = this.stateButton.getValue().helpKey;
        } else if (this.playButton != null && this.playButton.isMouseOver(mouseX, mouseY)) {
            helpKey = PLAY_HELP_KEY;
        }

        if (helpKey != null) {
            generatedTip.add(FormattedCharSequence.EMPTY);
            GuiHelpers.getTrimmedTextCollection(helpKey, TOOLTIP_WIDTH, STYLE_HELP)
                    .forEach(line -> generatedTip.add(line.getVisualOrderText()));
        }

        return generatedTip;
    }

    /**
     * The part of the tooltip that doesn't depend on the mouse: owner, id, title, category and credits. Built once
     * per row, which lives only as long as the screen.
     */
    private void buildSoundInfo(List<FormattedCharSequence> lines) {
        ResourceLocation id = this.config.soundEventId;
        this.resolveDisplayName(id.getNamespace())
                .ifPresent(name -> lines.add(FormattedCharSequence.forward(Objects.requireNonNull(ChatFormatting.stripFormatting(name)), STYLE_MOD_NAME)));

        lines.add(FormattedCharSequence.forward(id.toString(), STYLE_ID));

        // Never null; unknown sounds get default metadata
        SoundMetadata metadata = SOUND_LIBRARY.getSoundMetadata(id);
        if (metadata.hasTitle())
            lines.add(metadata.getTitle().getVisualOrderText());

        var soundSource = metadata.getCategory();
        int volumePercent = soundSource == SoundSource.MASTER ? 100 : (int) (GameUtils.getGameSettings().getSoundSourceVolume(soundSource) * 100);
        lines.add(Component.translatable("soundCategory." + soundSource.getName())
                .append(" (" + volumePercent + "%)")
                .withStyle(STYLE_CATEGORY)
                .getVisualOrderText());

        if (metadata.hasSubTitle())
            lines.add(metadata.getSubTitle().copy().withStyle(STYLE_SUBTITLE).getVisualOrderText());

        for (var credit : metadata.getCredits()) {
            lines.add(FormattedCharSequence.EMPTY);
            lines.add(credit.name().copy().withStyle(STYLE_CREDIT_NAME).getVisualOrderText());
            lines.add(credit.author().copy().withStyle(STYLE_CREDIT_AUTHOR).getVisualOrderText());
            credit.webSite().ifPresent(site -> lines.add(site.copy().withStyle(STYLE_CREDIT_AUTHOR).getVisualOrderText()));
            lines.add(credit.license().copy().withStyle(STYLE_CREDIT_LICENSE).getVisualOrderText());
        }

        if (id.getNamespace().equals("minecraft"))
            lines.add(Component.translatable(VANILLA_CREDIT_KEY).getVisualOrderText());
    }

    private Optional<String> resolveDisplayName(String namespace) {
        var displayName = ModInformation.getModDisplayName(namespace);
        if (displayName.isPresent())
            return displayName;

        // Could be a resource pack
        return GameUtils.getResourceManager().listPacks()
                .filter(pack -> pack.getNamespaces(PackType.CLIENT_RESOURCES).contains(namespace))
                .map(PackResources::packId)
                .findAny();
    }
}
