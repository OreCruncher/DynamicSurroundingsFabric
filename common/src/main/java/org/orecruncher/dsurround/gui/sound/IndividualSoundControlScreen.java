package org.orecruncher.dsurround.gui.sound;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.music.DSurroundMusicManager;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.sound.IAudioPlayer;

/**
 * Lets the player adjust the volume of, cull, or block individual sounds, and optionally play them.
 * <p>
 * Layout and event handling are left to vanilla ({@link HeaderAndFooterLayout}, widgets registered with
 * {@link #addRenderableWidget}) so as few methods as possible depend on signatures that change between Minecraft
 * versions.
 * <p>
 * When play buttons are enabled the screen owns the audio while it is open: it pauses the music and silences
 * other sounds on the way in, and stops everything and resumes the music on the way out.
 */
public class IndividualSoundControlScreen extends Screen {

    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT;
    private static final int HEADER_SPACING = 6;
    private static final int FOOTER_SPACING = 8;

    private static final int SEARCH_BAR_WIDTH = 200;
    private static final int SEARCH_BAR_HEIGHT = 20;

    // Same as vanilla's OptionsList: 20px controls with a 5px gap between rows
    private static final int ROW_HEIGHT = 25;
    private static final int MAX_ROW_WIDTH = 560;
    // Space left on each side of the rows, which includes room for the scrollbar
    private static final int ROW_SIDE_MARGIN = 30;

    private static final int TOOLTIP_Y_OFFSET = 30;

    private final @Nullable Screen parent;
    private final boolean enablePlay;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
    private EditBox searchField;
    private IndividualSoundControlList soundConfigList;

    /**
     * @param parent     the screen to return to, or null to return to the game
     * @param enablePlay whether rows get a play button. Only sensible when the game can be paused (single player or
     *                   no world loaded), since playing a sound ticks the sound manager.
     */
    public IndividualSoundControlScreen(final @Nullable Screen parent, final boolean enablePlay) {
        super(Component.translatable("dsurround.text.keybind.individualSoundConfig"));
        this.parent = parent;
        this.enablePlay = enablePlay;
    }

    @Override
    public void added() {
        super.added();
        if (this.enablePlay) {
            setMusicPaused(true);
            ContainerManager.resolve(IAudioPlayer.class).stopAll();
        }
    }

    @Override
    protected void init() {
        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(HEADER_SPACING));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title, this.font));
        this.searchField = header.addChild(new EditBox(this.font, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, Component.empty()));

        this.soundConfigList = this.layout.addToContents(new IndividualSoundControlList(
                GameUtils.getMC(),
                this.width,
                this.layout.getContentHeight(),
                this.layout.getHeaderHeight(),
                ROW_HEIGHT,
                this.enablePlay));
        this.searchField.setResponder(this.soundConfigList::setSearchFilter);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(FOOTER_SPACING));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.save()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
        this.setInitialFocus(this.searchField);
    }

    /**
     * Called on resize. The widgets are kept, so the search text, list contents and scroll position survive.
     */
    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.soundConfigList.updateSize(this.width, this.layout);
        // Never narrower than the controls need, even if that means the rows don't fit a very small window
        int minRowWidth = this.soundConfigList.getMinimumRowWidth();
        this.soundConfigList.setRowWidth(Mth.clamp(this.width - 2 * ROW_SIDE_MARGIN, minRowWidth, Math.max(minRowWidth, MAX_ROW_WIDTH)));
    }

    @Override
    public void tick() {
        this.soundConfigList.tick();

        // Need to tick the Sound Manager because when the game is paused, sounds are not
        // processed.  We do this to enable handling of the "play" button.  (If the game
        // is not paused, mobs and things will still wander around and can cause a
        // problem for the player while their head is buried in the menu.)
        if (this.enablePlay)
            GameUtils.getSoundManager().tick(false);
    }

    // Typing goes to the search box even when another control has focus

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        return super.keyPressed(event) || this.searchField.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return super.charTyped(event) || this.searchField.charTyped(event);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(context, mouseX, mouseY, partialTicks);

        // Offset downward so the tooltip doesn't cover the row's controls
        var entry = this.soundConfigList.getEntryAt(mouseX, mouseY);
        if (entry != null) {
            var toolTip = entry.getToolTip(mouseX, mouseY).stream().map(ClientTooltipComponent::create).toList();
            context.tooltip(this.font, toolTip, mouseX, mouseY + TOOLTIP_Y_OFFSET, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private void save() {
        // Gather the changes and push to underlying routine for parsing and packaging
        this.soundConfigList.saveChanges();
        this.onClose();
    }

    /**
     * Done, Cancel and Escape all end here. Returns to the parent screen, or to the game when there is none.
     */
    @Override
    public void onClose() {
        GameUtils.setScreen(this.parent);
    }

    /**
     * Called whenever this screen is replaced, however that happens. Stops any sound started with a play button
     * (and anything else left playing), then lets the music continue.
     */
    @Override
    public void removed() {
        this.soundConfigList.close();
        if (this.enablePlay) {
            GameUtils.getSoundManager().stop();
            setMusicPaused(false);
        }
        super.removed();
    }

    private static void setMusicPaused(boolean paused) {
        ReflectionHelper.cast(GameUtils.getMC().getMusicManager(), DSurroundMusicManager.class)
                .ifPresent(m -> m.setPaused(paused));
    }
}
