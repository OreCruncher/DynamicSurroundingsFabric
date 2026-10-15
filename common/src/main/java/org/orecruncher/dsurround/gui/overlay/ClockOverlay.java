package org.orecruncher.dsurround.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.ClockStyle;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.time.MinecraftClock;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.gui.ColorGradient;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.tags.ItemEffectTags;

/**
 * Shows the time while a clock is held or looked at in an item frame: as text above the hot bar, or as a pocket
 * watch above the crosshair: its hands at the time, a dome window under twelve showing day or night (as Minecraft's
 * clock does), and a window above six showing the season (or, without a seasons mod, the moon's phase). With the
 * margin option the watch is shown in a side or corner of the screen instead, while a clock is in the hot bar.
 */
public final class ClockOverlay extends AbstractOverlay {

    /**
     * Offset from the bottom of the screen for writing display text. 68 is the offset for the
     * "now playing" text that displays when playing a record in a jukebox. Position above that.
     */
    private static final int BOTTOM_OFFSET = 68 + 20;

    // The pocket watch atlas, sampled smoothly (its .mcmeta). Laid out here in units of a 256x256 grid; the file
    // has twice that resolution, which blit's UVs (fractions of the size given) don't need to know. The watch is
    // the top left 128x128, its dial centered at (64, 74); the glass (hub cap and glare) is the 128x128 below it.
    // The season and moon discs are 64x64 and turn about their centers, on the dial's center; the day disc is 32x32
    // and turns about its center, 10 above the dial's. The hands are 16x64, pointing up, and turn about (8, 48).
    static final ResourceLocation TEXTURE = Constants.asId("textures/pocket_watch.png");
    private static final int ATLAS_SIZE = 256;
    private static final int WATCH_SIZE = 128;
    private static final int DIAL_X = 64;
    private static final int DIAL_Y = 74;
    private static final int GLASS_V = 128;
    private static final int DISC_SIZE = 64;
    private static final int SEASON_DISC_U = 128;
    private static final int MOON_DISC_U = 192;
    private static final int DAY_DISC_U = 160;
    private static final int DAY_DISC_V = 64;
    private static final int DAY_DISC_SIZE = 32;
    private static final int DAY_DISC_Y = -10;
    private static final int HAND_WIDTH = 16;
    private static final int HAND_HEIGHT = 64;
    private static final int HAND_PIVOT_X = 8;
    private static final int HAND_PIVOT_Y = 48;
    private static final int HOUR_HAND_U = 128;
    private static final int MINUTE_HAND_U = 144;
    private static final int HANDS_V = 64;

    // 96 GUI pixels across at a scale of 1: about a texture pixel per screen pixel at GUI scale 3
    private static final float WATCH_DRAW_SCALE = 0.75F;
    // How far the watch is squashed vertically to lay it back, as the compass rose is (1 is upright). The disc and
    // hands turn before the squash, so they stay on the tilted face.
    private static final float WATCH_TILT = 1F;
    // Space between the bottom of the watch and the crosshair, and between the watch and the compass beside it
    private static final float CROSSHAIR_GAP = 10F;
    private static final float COMPASS_GAP = 8F;
    // In the margin the watch is smaller: 64 GUI pixels across at a scale of 1
    private static final float MARGIN_WATCH_DRAW_SCALE = 0.5F;

    // The season disc turns a quarter per season; early and late turn it a third of that either side, so the
    // icon is arriving or leaving the window. The moon disc turns an eighth per phase. Both ease to a new position
    // rather than jumping.
    static final float SUB_SEASON_TURN = 30F;
    static final float DISC_TURN_PER_TICK = 2F;

    // The hands tick once a game minute (1000 / 60 ticks). Each tick the minute hand overshoots its mark by a
    // little and settles back over the first part of the minute.
    static final double GAME_MINUTE = 1000D / 60D;
    static final double TICK_OVERSHOOT = 1.5D;
    static final double TICK_SETTLE = 0.15D;

    private final ITagLibrary tagLibrary;
    private final ISeasonalInformation seasonalInformation;
    private final Configuration config;
    private final CompassOverlay compass;
    private final MarginLayout marginLayout;
    private final MinecraftClock clock;
    private final ColorGradient gradient;
    private final ObjectArray<Component> clockDisplay = new ObjectArray<>(2);
    private boolean textShown;
    private ClockStyle style;
    private int renderWidth;
    private int renderHeight;
    private int color;

    private boolean watchShown;
    private boolean inMargin;
    private float scale;
    private int side;
    private long lastDayTime;
    private long dayTime;
    private boolean seasonDisc;
    private float lastDiscAngle;
    private float discAngle;

    public ClockOverlay(Configuration config, ITagLibrary tagLibrary, ISeasonalInformation seasonalInformation, CompassOverlay compass, MarginLayout marginLayout) {
        this.tagLibrary = tagLibrary;
        this.seasonalInformation = seasonalInformation;
        this.config = config;
        this.compass = compass;
        this.marginLayout = marginLayout;
        this.clock = new MinecraftClock();
        this.gradient = new ColorGradient(ColorPalette.DARK_VIOLET, ColorPalette.SUN_GLOW, 180F);
        this.style = this.config.compassAndClockOptions.clockStyle;
    }

    public void tick(Minecraft client) {
        boolean watchWasShown = this.watchShown;
        this.textShown = false;
        this.watchShown = false;
        this.inMargin = this.config.compassAndClockOptions.showInMargin;

        this.style = this.config.compassAndClockOptions.clockStyle;
        if (this.style.isShown() && GameUtils.isInGame()) {
            var player = GameUtils.getPlayer().orElseThrow();
            var mainHandShow = this.doShowClock(player.getMainHandItem());
            var offHandShow = this.doShowClock(player.getOffhandItem());
            var held = mainHandShow || offHandShow || this.doShowClock(GameUtils.getMC().crosshairPickEntity);

            // The text stays with holding the clock; in the margin the watch shows while one is in the hot bar
            if (held && this.style.showText()) {
                this.tickText(player);
                this.textShown = true;
            }
            if (this.style.showWatch() && (held || this.inMargin && MarginLayout.carries(player, this::doShowClock))) {
                // Beside the compass, if it is showing, on the side of the hand holding the clock
                var arm = mainHandShow ? player.getMainArm() : offHandShow ? player.getMainArm().getOpposite() : HumanoidArm.RIGHT;
                this.side = arm == HumanoidArm.RIGHT ? 1 : -1;
                this.tickWatch(player, !watchWasShown);
                this.watchShown = true;
            }
        }

        if (this.watchShown && this.inMargin) {
            float size = WATCH_SIZE * this.scale * MARGIN_WATCH_DRAW_SCALE;
            this.marginLayout.setWatch(size, size);
        } else {
            this.marginLayout.setWatch(0F, 0F);
        }
    }

    private void tickText(Player player) {
        this.clock.update(player.level());

        this.clockDisplay.clear();
        this.clockDisplay.add(this.clock.getFormattedTime());
        this.seasonalInformation.getCurrentSeasonTranslated().ifPresent(this.clockDisplay::add);

        var textRender = GameUtils.getTextRenderer();
        this.renderWidth = textRender.width(this.clockDisplay.get(0));
        if (this.clockDisplay.size() > 1)
            this.renderWidth = Math.max(this.renderWidth, textRender.width(this.clockDisplay.get(1)));

        this.renderHeight = this.clockDisplay.size() == 1 ? textRender.lineHeight - 2 : textRender.lineHeight * 2;

        // Calculate the color this tick
        this.color = textColor(this.gradient, player.level().getTimeOfDay(1F));
    }

    private void tickWatch(Player player, boolean justShown) {
        this.scale = (float) this.config.compassAndClockOptions.scale;

        // The hands follow the day time between ticks; a jump (the time being set) moves them straight there
        var time = player.level().getDayTime();
        this.lastDayTime = justShown || Math.abs(time - this.dayTime) > 100 ? time : this.dayTime;
        this.dayTime = time;

        // The day disc follows the time as the hands do; the lower disc eases to the season or the moon's phase
        this.seasonDisc = this.seasonalInformation.hasSeasons();
        var target = this.seasonDisc ? seasonDiscAngle(this.season(), this.subSeason()) : moonDiscAngle(player.level().getMoonPhase());
        this.lastDiscAngle = justShown ? target : this.discAngle;
        this.discAngle = justShown ? target : turnToward(this.discAngle, target, DISC_TURN_PER_TICK);
    }

    private int season() {
        if (this.seasonalInformation.isSummer())
            return 1;
        if (this.seasonalInformation.isAutumn())
            return 2;
        if (this.seasonalInformation.isWinter())
            return 3;
        return 0;
    }

    private int subSeason() {
        if (this.seasonalInformation.isEarly())
            return -1;
        if (this.seasonalInformation.isLate())
            return 1;
        return 0;
    }

    /**
     * The clock text's color for the time of day (0 to 1, from Level.getTimeOfDay): along the gradient from
     * midnight (0 degrees) to noon (180), and back. Fully opaque: newer versions draw text with zero alpha
     * invisibly, where this one makes it opaque.
     */
    static int textColor(ColorGradient gradient, float timeOfDay) {
        // 0 is noon, 180 is midnight. Need to normalize so that midnight 0.
        var angleDegrees = timeOfDay * 360F + 180;
        // Wrap
        if (angleDegrees >= 360)
            angleDegrees -= 360;
        // Are we to decrease rather than increase toward noon?
        if (angleDegrees >= 180)
            angleDegrees = 360 - angleDegrees;
        return 0xFF000000 | gradient.getRGBColor(angleDegrees);
    }

    /**
     * The hour hand's angle, in degrees clockwise from twelve, for a day time (0 is 6 AM). It goes around twice a
     * day, as a watch's does.
     */
    static float hourHandAngle(double dayTime) {
        return (float) (Mth.positiveModulo(dayTime / 1000D + 6D, 12D) * 30D);
    }

    /**
     * The minute hand's angle, in degrees clockwise from twelve: around once an hour (1000 ticks)
     */
    static float minuteHandAngle(double dayTime) {
        return (float) (Mth.positiveModulo(dayTime, 1000D) * 0.36D);
    }

    /**
     * The day time the hands show: whole game minutes, so they tick from one minute mark to the next (a game
     * minute is a little under a second) rather than sweeping
     */
    static double tickedTime(double dayTime) {
        return Math.floor(dayTime / GAME_MINUTE) * GAME_MINUTE;
    }

    /**
     * How far past its mark the minute hand is, in degrees, just after a tick: it overshoots and settles back
     */
    static float tickOvershoot(double dayTime) {
        var sinceTick = Mth.positiveModulo(dayTime, GAME_MINUTE) / GAME_MINUTE;
        return sinceTick < TICK_SETTLE ? (float) (TICK_OVERSHOOT * (1D - sinceTick / TICK_SETTLE)) : 0F;
    }

    /**
     * How far to turn the day disc, in degrees clockwise: the sun (at the disc's top) up in the dome window at
     * noon, the moon (at its bottom) at midnight, telling noon from midnight on a twelve hour dial
     */
    static float dayDiscAngle(double dayTime) {
        return (float) (Mth.positiveModulo(dayTime - 6000D, 24000D) * 360D / 24000D);
    }

    /**
     * How far to turn the season disc, in degrees clockwise, to bring a season (0 spring to 3 winter) to the
     * window: centered mid-season (sub-season 0), arriving early (-1) and leaving late (1)
     */
    static float seasonDiscAngle(int season, int subSeason) {
        return Mth.positiveModulo(season * 90F + subSeason * SUB_SEASON_TURN, 360F);
    }

    /**
     * How far to turn the moon disc, in degrees clockwise, to bring a phase (Level.getMoonPhase: 0 full, 4 new) to
     * the window
     */
    static float moonDiscAngle(int phase) {
        return Mth.positiveModulo(phase, 8) * 45F;
    }

    /**
     * Turns an angle toward a target by at most a step, the shorter way around (0 to 360)
     */
    static float turnToward(float current, float target, float maxStep) {
        var delta = Mth.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep)
            return Mth.positiveModulo(target, 360F);
        return Mth.positiveModulo(current + Math.signum(delta) * maxStep, 360F);
    }

    private boolean doShowClock(ItemStack stack) {
        return this.tagLibrary.is(ItemEffectTags.CLOCKS, stack);
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
        if (this.watchShown) {
            if (this.inMargin) {
                // The placement is the watch's center; the dial is below it
                float size = this.scale * MARGIN_WATCH_DRAW_SCALE;
                var placement = this.marginLayout.place(this.config.compassAndClockOptions.marginPosition, context.guiWidth(), context.guiHeight());
                this.renderWatch(context, partialTick, placement.watchX(), placement.watchY() + (DIAL_Y - WATCH_SIZE / 2F) * size, size);
            } else {
                // Above the crosshair, its bottom just clear of it; beside the compass if that is showing
                float size = this.scale * WATCH_DRAW_SCALE;
                float x = context.guiWidth() / 2F;
                if (this.compass.isShowing())
                    x += this.side * (this.compass.halfWidth() + COMPASS_GAP + WATCH_SIZE * size / 2F);
                float y = context.guiHeight() / 2F - CROSSHAIR_GAP - (WATCH_SIZE - DIAL_Y) * size * WATCH_TILT;
                this.renderWatch(context, partialTick, x, y, size);
            }
        }
        if (this.textShown)
            this.renderText(context);
    }

    /**
     * Draws the watch with its dial's center at x, y
     */
    private void renderWatch(GuiGraphics context, float partialTick, float x, float y, float size) {
        var time = Mth.lerp(partialTick, (double) this.lastDayTime, (double) this.dayTime);
        float disc = Mth.rotLerp(partialTick, this.lastDiscAngle, this.discAngle);
        // The hands tick; the overshoot only while time runs, or a stopped clock would hold it
        var handTime = tickedTime(time);
        float overshoot = this.dayTime != this.lastDayTime ? tickOvershoot(time) : 0F;

        // The atlas has soft edges; this version's blit doesn't set up blending itself
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var pose = context.pose();
        pose.pushPose();
        try {
            // From here on, in atlas pixels with the dial's center at the origin
            pose.translate(x, y, 0F);
            pose.scale(size, size * WATCH_TILT, 1F);

            // The discs show through the windows in the dial, so they go first; the glass goes over the hands
            this.drawTurned(context, disc, 0, this.seasonDisc ? SEASON_DISC_U : MOON_DISC_U, 0, DISC_SIZE, DISC_SIZE, DISC_SIZE / 2, DISC_SIZE / 2);
            this.drawTurned(context, dayDiscAngle(time), DAY_DISC_Y, DAY_DISC_U, DAY_DISC_V, DAY_DISC_SIZE, DAY_DISC_SIZE, DAY_DISC_SIZE / 2, DAY_DISC_SIZE / 2);
            context.blit(TEXTURE, -DIAL_X, -DIAL_Y, 0, 0, WATCH_SIZE, WATCH_SIZE, ATLAS_SIZE, ATLAS_SIZE);
            this.drawTurned(context, hourHandAngle(handTime), 0, HOUR_HAND_U, HANDS_V, HAND_WIDTH, HAND_HEIGHT, HAND_PIVOT_X, HAND_PIVOT_Y);
            this.drawTurned(context, minuteHandAngle(handTime) + overshoot, 0, MINUTE_HAND_U, HANDS_V, HAND_WIDTH, HAND_HEIGHT, HAND_PIVOT_X, HAND_PIVOT_Y);
            context.blit(TEXTURE, -DIAL_X, -DIAL_Y, 0, GLASS_V, WATCH_SIZE, WATCH_SIZE, ATLAS_SIZE, ATLAS_SIZE);
        } finally {
            pose.popPose();
            RenderSystem.disableBlend();
        }
    }

    /**
     * Draws part of the atlas turned clockwise on screen about its pivot, the pivot placed a height above (less
     * than 0) or below the dial's center
     */
    private void drawTurned(GuiGraphics context, float degrees, int y, int u, int v, int width, int height, int pivotX, int pivotY) {
        var pose = context.pose();
        pose.pushPose();
        try {
            pose.translate(0F, y, 0F);
            pose.mulPose(Axis.ZP.rotationDegrees(degrees));
            context.blit(TEXTURE, -pivotX, -pivotY, u, v, width, height, ATLAS_SIZE, ATLAS_SIZE);
        } finally {
            pose.popPose();
        }
    }

    private void renderText(GuiGraphics context) {
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
