package org.orecruncher.dsurround.gui.overlay.lightlevel;

import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.LightLevelColors;
import org.orecruncher.dsurround.config.LightLevelMode;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.eventing.IBlockUpdates;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.IClientDisconnect;
import org.orecruncher.dsurround.eventing.ICollectDiagnostics;
import org.orecruncher.dsurround.eventing.ILightUpdate;
import org.orecruncher.dsurround.eventing.IResourceReload;
import org.orecruncher.dsurround.gui.overlay.AbstractOverlay;
import org.orecruncher.dsurround.gui.overlay.MarginLayout;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.util.Locale;

/**
 * Shows the light level on the blocks around the player that a monster could stand on, colored by whether one could
 * spawn there, turned on and off with a key. The numbers lie flat on the blocks, drawn in the world by
 * LightLevelRenderer (not yet on 26.2). On screen, a legend in the bottom right corner says what the colors mean: above the
 * compass and watch if they are shown in that corner, and above the hot bar if the screen is too narrow for both.
 * <p>
 * The work is kept off the frame: {@link LightLevelField} keeps what it found a section at a time and looks again
 * only where something changed; each tick the colors and numbers for every combination of light are worked out once
 * (for the sky's darkness now), and a frame only looks them up and writes a quad per block in view.
 */
public final class LightLevelOverlay extends AbstractOverlay {

    private static final int LEGEND_BACKGROUND = 0x90505050;
    private static final int LEGEND_TEXT = 0xFFE0E0E0;
    private static final int LEGEND_PADDING = 3;
    private static final int SWATCH_SIZE = 7;
    private static final int SWATCH_GAP = 4;
    // Space between the legend and the screen's edges, as the compass and watch keep in the margin
    private static final int EDGE_GAP = 6;
    // The hot bar is 182 wide, centered; above it the health, armor and food bars
    private static final int HOT_BAR_HALF_WIDTH = 91;
    private static final int HOT_BAR_CLEARANCE = 60;

    private final Configuration config;
    private final MarginLayout marginLayout;
    private final LightLevelField field = new LightLevelField();
    private final LightLevelAtlas atlas = new LightLevelAtlas();
    private final int[] colors = new int[LightLevels.TABLE_SIZE];
    private final byte[] values = new byte[LightLevels.TABLE_SIZE];

    private boolean enabled;
    private ClientLevel level;
    private int playerX;
    private int playerY;
    private int playerZ;
    private int range;
    private int labelsDrawn;

    // The legend: a title, then a row per color, worked out again when what it says changes
    private final ObjectArray<FormattedCharSequence> legendText = new ObjectArray<>(5);
    private final int[] legendColors = new int[4];
    private boolean legendShown;
    private boolean legendStale = true;
    private LightLevelMode legendMode;
    private LightLevelColors legendPalette;
    private boolean legendHideSafe;
    private int legendWidth;

    public LightLevelOverlay(Configuration config, MarginLayout marginLayout) {
        this.config = config;
        this.marginLayout = marginLayout;

        IBlockUpdates.EVENT.register(this::blockUpdates);
        ILightUpdate.EVENT.register(this::lightUpdated);
        IChunkLoad.EVENT.register(this::chunkLoaded);
        IResourceReload.EVENT.register(resourceManager -> {
            // The font, or the language, may have changed
            this.atlas.invalidate();
            this.legendStale = true;
        });
        IClientDisconnect.EVENT.register(client -> this.setEnabled(false));
        ICollectDiagnostics.EVENT.register(this::onCollect);
    }

    public void toggle() {
        this.setEnabled(!this.enabled);
        Library.LOGGER.info("Light level overlay: %s", this.enabled ? "on" : "off");
    }

    private void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.field.clear();
            this.level = null;
            this.labelsDrawn = 0;
            this.legendShown = false;
        }
    }

    /**
     * Whether the numbers are drawn this frame
     */
    boolean isDrawing() {
        return this.enabled && this.level != null && this.atlas.isReady();
    }

    LightLevelField field() {
        return this.field;
    }

    int[] colors() {
        return this.colors;
    }

    byte[] values() {
        return this.values;
    }

    int playerX() {
        return this.playerX;
    }

    int playerY() {
        return this.playerY;
    }

    int playerZ() {
        return this.playerZ;
    }

    int range() {
        return this.range;
    }

    void setLabelsDrawn(int count) {
        this.labelsDrawn = count;
    }

    @Override
    public void tick(Minecraft client) {
        this.legendShown = false;
        if (!this.enabled)
            return;
        var player = GameUtils.getPlayer();
        if (player.isEmpty() || !(player.get().level() instanceof ClientLevel current)) {
            this.field.clear();
            this.level = null;
            return;
        }

        // Another dimension: nothing found in the last one applies
        if (current != this.level) {
            this.field.clear();
            this.level = current;
        }

        var options = this.config.lightLevelOptions;
        BlockPos pos = player.get().blockPosition();
        this.playerX = pos.getX();
        this.playerY = pos.getY();
        this.playerZ = pos.getZ();
        this.range = options.range;
        this.field.update(current, this.playerX, this.playerY, this.playerZ, this.range);

        // A storm darkens the sky as far as Monster.isDarkEnoughToSpawn counts it
        int skyDarken = current.isThundering() ? LightLevels.THUNDER_SKY : current.getSkyDarken();
        var dimension = current.dimensionType();
        LightLevels.fillTables(this.colors, this.values, options.mode, options.colors, options.hideSafe, skyDarken,
                dimension.monsterSpawnBlockLightLimit(), dimension.monsterSpawnLightTest().maxInclusive());

        this.atlas.refresh();

        // Out of the way of the F3 screen, as the diagnostics are
        this.legendShown = options.showLegend && this.atlas.isReady() && !client.getDebugOverlay().showDebugScreen();
        if (this.legendShown)
            this.tickLegend(options.mode, options.colors, options.hideSafe);
    }

    private void tickLegend(LightLevelMode mode, LightLevelColors palette, boolean hideSafe) {
        if (!this.legendStale && mode == this.legendMode && palette == this.legendPalette && hideSafe == this.legendHideSafe)
            return;
        this.legendStale = false;
        this.legendMode = mode;
        this.legendPalette = palette;
        this.legendHideSafe = hideSafe;

        var font = GameUtils.getTextRenderer();
        this.legendText.clear();
        var title = Component.translatable("dsurround.text.lightLevel.legend.title",
                Component.translatable("dsurround.text.lightLevel.legend.mode." + mode.name()));
        this.legendText.add(title.getVisualOrderText());
        this.legendWidth = font.width(title);

        var kinds = LightLevels.legendKinds(hideSafe);
        for (int i = 0; i < kinds.length; i++) {
            var text = Component.translatable("dsurround.text.lightLevel.legend." + kinds[i].name());
            this.legendText.add(text.getVisualOrderText());
            this.legendColors[i] = LightLevels.color(kinds[i], palette, false);
            this.legendWidth = Math.max(this.legendWidth, SWATCH_SIZE + SWATCH_GAP + font.width(text));
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, float partialTick) {
        // The numbers themselves are drawn in the world
        if (this.legendShown && this.enabled)
            this.renderLegend(context);
    }

    private void renderLegend(GuiGraphicsExtractor context) {
        var font = GameUtils.getTextRenderer();
        int lineHeight = font.lineHeight;
        int width = this.legendWidth + 2 * LEGEND_PADDING;
        int height = this.legendText.size() * lineHeight + 2 * LEGEND_PADDING - 1;

        int right = context.guiWidth() - EDGE_GAP;
        int left = right - width;
        int bottom = context.guiHeight() - EDGE_GAP;
        // Above the compass and watch if they are in this corner; above the hot bar if it reaches under the legend
        float reach = this.marginLayout.bottomRightReach(this.config.compassAndClockOptions.marginPosition);
        if (reach > 0F)
            bottom = context.guiHeight() - Math.round(reach) - EDGE_GAP;
        if (left < context.guiWidth() / 2 + HOT_BAR_HALF_WIDTH + EDGE_GAP)
            bottom = Math.min(bottom, context.guiHeight() - HOT_BAR_CLEARANCE);
        int top = bottom - height;

        context.fill(left, top, right, bottom, LEGEND_BACKGROUND);
        int x = left + LEGEND_PADDING;
        int y = top + LEGEND_PADDING;
        context.text(font, this.legendText.get(0), x, y, LEGEND_TEXT, false);
        for (int i = 1; i < this.legendText.size(); i++) {
            y += lineHeight;
            // A swatch with a dark edge, as the numbers have
            int swatchTop = y + (lineHeight - 1 - SWATCH_SIZE) / 2;
            context.fill(x, swatchTop, x + SWATCH_SIZE, swatchTop + SWATCH_SIZE, 0xFF000000);
            context.fill(x + 1, swatchTop + 1, x + SWATCH_SIZE - 1, swatchTop + SWATCH_SIZE - 1, this.legendColors[i - 1]);
            context.text(font, this.legendText.get(i), x + SWATCH_SIZE + SWATCH_GAP, y, LEGEND_TEXT, false);
        }
    }

    private void blockUpdates(LongCollection positions) {
        if (!this.enabled)
            return;
        var it = positions.iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            this.field.blockChanged(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
        }
    }

    private void lightUpdated(LightLayer layer, SectionPos section) {
        if (this.enabled)
            this.field.lightChanged(section.x(), section.y(), section.z());
    }

    private void chunkLoaded(ClientLevel level, ChunkPos chunkPos) {
        if (this.enabled)
            this.field.chunkChanged(chunkPos.x(), chunkPos.z());
    }

    private void onCollect(CollectDiagnosticsEvent event) {
        if (!this.enabled)
            return;
        var text = String.format(Locale.ROOT, "Light levels: %d sections, %d scanned last tick, %d labels",
                this.field.sectionCount(), this.field.scansLastTick(), this.labelsDrawn);
        event.add(CollectDiagnosticsEvent.Section.Systems, text);
    }
}
