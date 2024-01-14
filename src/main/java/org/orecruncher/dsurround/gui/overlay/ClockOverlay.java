package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.gui.ColorGradient;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.tags.ItemEffectTags;

public class ClockOverlay extends AbstractOverlay {

    /**
     * Offset from the bottom of the screen for writing display text. 68 is the offset for the
     * "now playing" text that displays when playing a record in a jukebox. Position above that.
     */
    private static final int BOTTOM_OFFSET = 68 + 20;

    private final ITagLibrary tagLibrary;
    private final Configuration config;
    private final MinecraftClock clock;
    private final ColorGradient gradient;
    private boolean showClock;
    private int textWidth;
    private String clockText;
    private int color;

    public ClockOverlay(Configuration config, ITagLibrary tagLibrary) {
        this.tagLibrary = tagLibrary;
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

            this.showClock = this.doShowClock(mainHandItem) || this.doShowClock(offHandItem);
            this.clock.update(player.level());
            this.clockText = this.clock.getFormattedTime();
            this.textWidth = GameUtils.getTextRenderer().width(clockText);

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

    @Override
    public void render(GuiGraphics context, float partialTick) {
        if (!this.showClock)
            return;

        var textRender = GameUtils.getTextRenderer();
        var x = (context.guiWidth() - this.textWidth) / 2;
        var y = context.guiHeight() - BOTTOM_OFFSET;

        // Don't use renderTooltip. It uses a Z which pushes the rendering to the top of the Z stack and can
        // and can interfere with renders.
        TooltipRenderUtil.renderTooltipBackground(context, x, y, this.textWidth, textRender.lineHeight - 2, 0);
        context.drawString(textRender, this.clockText, x, y, this.color, false);
    }
}
