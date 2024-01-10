package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.DayCycle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.tags.ItemEffectTags;

import java.util.EnumMap;
import java.util.Map;

public class ClockOverlay extends AbstractOverlay {

    /**
     * Offset from the bottom of the screen for writing display text. 68 is the offset for the
     * "now playing" text that displays when playing a record in a jukebox. Position above that.
     */
    private static final int BOTTOM_OFFSET = 68 + 20;

    private static final Map<DayCycle, TextColor> COLOR_MAP = new EnumMap<>(DayCycle.class);

    static {
        COLOR_MAP.put(DayCycle.NO_SKY, ColorPalette.GOLD);
        COLOR_MAP.put(DayCycle.DAYTIME, ColorPalette.GOLD);
        COLOR_MAP.put(DayCycle.NIGHTTIME, ColorPalette.GREEN);
        COLOR_MAP.put(DayCycle.SUNRISE, ColorPalette.YELLOW);
        COLOR_MAP.put(DayCycle.SUNSET, ColorPalette.ORANGE);
    }

    private final ITagLibrary tagLibrary;
    private final Configuration config;
    private final MinecraftClock clock;
    private String clockText;
    private TextColor clockColor;
    private boolean showClock;

    public ClockOverlay(Configuration config, ITagLibrary tagLibrary) {
        this.tagLibrary = tagLibrary;
        this.config = config;
        this.clock = new MinecraftClock();
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
            this.clockColor = COLOR_MAP.get(this.clock.getCycle());
        }
    }

    private boolean doShowClock(ItemStack stack) {
        return !stack.isEmpty() && this.tagLibrary.is(ItemEffectTags.CLOCKS, stack);
    }

    @Override
    public void render(GuiGraphics context, float partialTick) {
        if (!this.showClock)
            return;

        var matrixStack = context.pose();

        try {
            matrixStack.pushPose();
            var textRender = GameUtils.getTextRenderer();

            matrixStack.translate(context.guiWidth() / 2.0F, context.guiHeight() - BOTTOM_OFFSET, 0.0f);
            var o = textRender.width(this.clockText);
            context.drawString(textRender, this.clockText, -o / 2, -4, this.clockColor.getValue(), true);

        } finally {
            matrixStack.popPose();
        }
    }
}
