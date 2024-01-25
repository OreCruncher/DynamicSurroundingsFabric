package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.gui.ColorGradient;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.tags.ItemEffectTags;

public class ClockOverlay extends AbstractOverlay {

    /**
     * Offset from the bottom of the screen for writing display text. 68 is the offset for the
     * "now playing" text that displays when playing a record in a jukebox. Position above that.
     */
    private static final int BOTTOM_OFFSET = 68 + 20;

    private final ITagLibrary tagLibrary;
    private final ISeasonalInformation seasonalInformation;
    private final Configuration config;
    private final MinecraftClock clock;
    private final ColorGradient gradient;
    private boolean showClock;
    private int renderWidth;
    private int renderHeight;
    private ObjectArray<String> clockDisplay = new ObjectArray<>(2);
    private int color;

    public ClockOverlay(Configuration config, ITagLibrary tagLibrary, ISeasonalInformation seasonalInformation) {
        this.tagLibrary = tagLibrary;
        this.seasonalInformation = seasonalInformation;
        this.config = config;
        this.clock = new MinecraftClock();
        this.gradient = new ColorGradient(ColorPalette.DARK_VIOLET, ColorPalette.SUN_GLOW, 180F);
        this.showClock = false;
    }

    public void tick(Minecraft client) {
        this.showClock = false;

        if (this.config.compassAndClockOptions.enableClock && GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            var mainHandItem = player.getMainHandItem();
            var offHandItem = player.getOffhandItem();

            this.showClock = this.doShowClock(mainHandItem) || this.doShowClock(offHandItem) || this.doShowClock(GameUtils.getMC().crosshairPickEntity);
            this.clock.update(player.level());

            this.clockDisplay.clear();
            this.clockDisplay.add(this.clock.getFormattedTime());
            this.seasonalInformation.getCurrentSeasonTranslated(player.level()).ifPresent(s -> this.clockDisplay.add(s));

            var textRender = GameUtils.getTextRenderer();
            this.renderWidth = textRender.width(this.clockDisplay.get(0));
            if (this.clockDisplay.size() > 1)
                this.renderWidth = Math.max(this.renderWidth, textRender.width(this.clockDisplay.get(1)));

            this.renderHeight = this.clockDisplay.size() == 1 ? textRender.lineHeight - 2 : textRender.lineHeight * 2;

            // Calculate the color this tick
            var world = player.level();
            // 0 is noon, 180 is midnight. Need to normalize so that midnight 0.
            var angleDegrees = world.getTimeOfDay(1F)* 360F + 180;
            // Wrap
            if (angleDegrees >= 360)
                angleDegrees -= 360;
            // Are we to decrease rather than increase toward noon?
            if (angleDegrees >= 180)
                angleDegrees = 360 - angleDegrees;

            this.color = this.gradient.getRGBColor(angleDegrees);
        }
    }

    private boolean doShowClock(ItemStack stack) {
        return !stack.isEmpty() && this.tagLibrary.is(ItemEffectTags.CLOCKS, stack);
    }

    private boolean doShowClock(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            var itemInFrame = frame.getItem();
            return this.doShowClock(itemInFrame);
        }
        return false;
    }

    @Override
    public void render(GuiGraphics context, float partialTick) {
        if (!this.showClock)
            return;

        var textRender = GameUtils.getTextRenderer();
        var x = context.guiWidth() / 2;
        var y = context.guiHeight() - BOTTOM_OFFSET;

        if (this.clockDisplay.size() == 2)
            y -= textRender.lineHeight / 2;

        // Don't use renderTooltip. It uses a Z which pushes the rendering to the top of the Z stack and can
        // and can interfere with renders.
        TooltipRenderUtil.renderTooltipBackground(context, x - this.renderWidth / 2, y, this.renderWidth, this.renderHeight, 0);
        context.drawCenteredString(textRender, this.clockDisplay.get(0), x, y, this.color);
        if (this.clockDisplay.size() == 2)
            context.drawCenteredString(textRender, this.clockDisplay.get(1), x, y + textRender.lineHeight, this.color);
    }
}
