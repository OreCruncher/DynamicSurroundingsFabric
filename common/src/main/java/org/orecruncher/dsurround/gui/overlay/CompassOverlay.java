package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.CompassStyle;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.tags.ItemEffectTags;

/**
 * Shows a compass above the crosshair while a compass is held: a band that slides with the heading, or a rose
 * laid flat in front of the player that turns with it. Or, with the margin option, in a side or corner of the
 * screen while a compass is in the hot bar, the rose upright.
 */
public final class CompassOverlay extends AbstractOverlay {

    // Vertical offset to avoid writing over the cross-hair
    private static final int CROSSHAIR_OFFSET = 60;

    // The texture is 512x512. Each style has two rows of bands: the first half of the heading on the first, the
    // second half on the second. The rows touch and the texture is sampled smoothly (its .mcmeta), so a band is
    // read half a texel in from its top and bottom, or its neighbors show along its edges.
    private static final int TEXTURE_SIZE = 512;
    private static final int HALF_TEXTURE_SIZE = TEXTURE_SIZE / 2;
    private static final int BAND_WIDTH = 65 * 2;
    private static final int BAND_HEIGHT = 12 * 2;

    // Rose textures are 256x256, drawn at three quarters size and sampled smoothly (their .mcmeta). Tilting a rose
    // back 70 degrees under the GUI's orthographic projection only squashes it vertically, so a scale does it (and
    // works with 26.2's 2D GUI pose).
    private static final int ROSE_SIZE = 256;
    private static final float ROSE_DRAW_SCALE = 0.75F;
    private static final float ROSE_TILT = Mth.cos(65F * Mth.DEG_TO_RAD);
    // In the margin the rose is upright, and smaller: 64 GUI pixels across at a scale of 1
    private static final float MARGIN_ROSE_DRAW_SCALE = 0.25F;
    // And the band at half size, about the width of the watch beside it
    private static final float MARGIN_BAND_DRAW_SCALE = 0.5F;

    private final ITagLibrary tagLibrary;
    private final IDimensionOracle dimensionInformation;
    private final Configuration config;
    private final MarginLayout marginLayout;
    private final CompassWobble wobbler;
    private boolean showCompass;
    private boolean inMargin;
    private boolean spinRandomly;
    private float scale;
    private CompassStyle style;

    public CompassOverlay(Configuration config, ITagLibrary tagLibrary, IDimensionOracle dimensionInformation, MarginLayout marginLayout) {
        this.tagLibrary = tagLibrary;
        this.marginLayout = marginLayout;
        this.dimensionInformation = dimensionInformation;
        this.config = config;
        this.wobbler = new CompassWobble();
        this.showCompass = false;
        this.style = this.config.compassAndClockOptions.compassStyle;
        this.scale = (float) this.config.compassAndClockOptions.scale;
    }

    public void tick(Minecraft client) {
        this.showCompass = false;
        this.spinRandomly = false;
        this.inMargin = this.config.compassAndClockOptions.showInMargin;

        this.style = this.config.compassAndClockOptions.compassStyle;
        if (this.style.isShown() && GameUtils.isInGame()) {
            this.scale = (float) this.config.compassAndClockOptions.scale;

            var player = GameUtils.getPlayer().orElseThrow();
            if (this.inMargin) {
                // Spins unless one of the compasses carried works here
                this.showCompass = MarginLayout.carries(player, this::doShowCompass);
                this.spinRandomly = this.showCompass
                        && !MarginLayout.carries(player, stack -> this.doShowCompass(stack) && !this.doCompassSpin(stack));
            } else {
                var mainHandItem = player.getMainHandItem();
                var offHandItem = player.getOffhandItem();

                var mainHandShow = this.doShowCompass(mainHandItem);
                var offHandShow = this.doShowCompass(offHandItem);
                this.showCompass = mainHandShow || offHandShow;
                this.spinRandomly = shouldSpin(mainHandShow, mainHandShow && this.doCompassSpin(mainHandItem),
                        offHandShow, offHandShow && this.doCompassSpin(offHandItem));
            }

            this.wobbler.update(player.level().getGameTime());
        }

        if (this.showCompass && this.inMargin)
            this.marginLayout.setCompass(this.width(), this.height());
        else
            this.marginLayout.setCompass(0F, 0F);
    }

    /**
     * Whether the compass band spins randomly: if a compass held in either hand is one that wobbles (in a dimension
     * where compasses wobble). Decided afresh each tick from what is held now.
     */
    static boolean shouldSpin(boolean mainHandCompass, boolean mainHandWobbles, boolean offHandCompass, boolean offHandWobbles) {
        return (mainHandCompass && mainHandWobbles) || (offHandCompass && offHandWobbles);
    }

    /**
     * Whether the compass is shown above the crosshair this tick: the pocket watch moves aside for it
     */
    public boolean isShowing() {
        return this.showCompass && !this.inMargin;
    }

    /**
     * Half the compass's width on screen, from the center of the screen
     */
    public float halfWidth() {
        return this.width() / 2F;
    }

    private float width() {
        if (!this.style.isRose())
            return BAND_WIDTH * this.bandScale();
        return ROSE_SIZE * this.scale * (this.inMargin ? MARGIN_ROSE_DRAW_SCALE : ROSE_DRAW_SCALE);
    }

    private float height() {
        return this.style.isRose() ? this.width() : BAND_HEIGHT * this.bandScale();
    }

    private float bandScale() {
        return this.inMargin ? this.scale * MARGIN_BAND_DRAW_SCALE : this.scale;
    }

    private boolean doShowCompass(ItemStack stack) {
        return this.tagLibrary.is(ItemEffectTags.COMPASSES, stack);
    }

    private boolean doCompassSpin(ItemStack stack) {
        return this.dimensionInformation.getCompassWobble() && this.tagLibrary.is(ItemEffectTags.COMPASS_WOBBLE, stack);
    }

    @Override
    public void render(GuiGraphics context, float partialTick) {
        if (!this.showCompass)
            return;

        float rotation;
        if (this.spinRandomly) {
            rotation = this.wobbler.getRandomlySpinningRotation(partialTick);
        } else {
            final var player = GameUtils.getPlayer().orElseThrow();
            rotation = player.getViewYRot(partialTick);
        }

        // The textured GUI pipeline blends, so the transparent styles and the roses need nothing more
        if (this.inMargin) {
            var placement = this.marginLayout.place(this.config.compassAndClockOptions.marginPosition, context.guiWidth(), context.guiHeight());
            if (this.style.isRose())
                this.renderRose(context, rotation, placement.compassX(), placement.compassY(), this.scale * MARGIN_ROSE_DRAW_SCALE, 1F);
            else
                this.renderBand(context, rotation, placement.compassX() - this.width() / 2F, placement.compassY() - this.height() / 2F);
        } else if (this.style.isRose()) {
            // Centered where the band's center is, above the crosshair
            this.renderRose(context, rotation, context.guiWidth() / 2F, (context.guiHeight() - CROSSHAIR_OFFSET) / 2F, this.scale * ROSE_DRAW_SCALE, ROSE_TILT);
        } else {
            // Centered horizontally, above the crosshair
            this.renderBand(context, rotation, (context.guiWidth() - this.width()) / 2F, (context.guiHeight() - CROSSHAIR_OFFSET - this.height()) / 2F);
        }
    }

    private void renderBand(GuiGraphics context, float rotation, float x, float y) {
        // Which part of the band to show: the heading, as a position across the texture
        int u = Mth.floor(((rotation * TEXTURE_SIZE) / 360F) + 0.5D) & (TEXTURE_SIZE - 1);
        float v = this.style.getSpriteNumber() * (BAND_HEIGHT * 2);
        if (u >= HALF_TEXTURE_SIZE) {
            u -= HALF_TEXTURE_SIZE;
            v += BAND_HEIGHT;
        }

        var pose = context.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(this.bandScale(), this.bandScale());
            context.blit(RenderPipelines.GUI_TEXTURED, this.style.getTexture(), 0, 0, u, v + 0.5F, BAND_WIDTH, BAND_HEIGHT, BAND_WIDTH, BAND_HEIGHT - 1, TEXTURE_SIZE, TEXTURE_SIZE);
        } finally {
            pose.popMatrix();
        }
    }

    private void renderRose(GuiGraphics context, float rotation, float x, float y, float size, float tilt) {
        var pose = context.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(size, size * tilt);
            pose.rotate(roseAngle(rotation) * Mth.DEG_TO_RAD);
            context.blit(RenderPipelines.GUI_TEXTURED, this.style.getTexture(), -ROSE_SIZE / 2, -ROSE_SIZE / 2, 0, 0, ROSE_SIZE, ROSE_SIZE, ROSE_SIZE, ROSE_SIZE);
        } finally {
            pose.popMatrix();
        }
    }

    /**
     * How far to turn a rose, in degrees clockwise on screen, so the heading points to the top of the screen (away
     * from the player). A yaw of 0 faces south.
     */
    static float roseAngle(float yaw) {
        return -Mth.wrapDegrees(yaw + 180F);
    }

    /**
     * Cloned from the Minecraft compass code
     */
    static class CompassWobble {
        private static final int TICK_DELAY = 5;
        static final float MAX_DELTA_TICK = 1F / 20F;
        private float targetRotation;
        private float lastRotation;
        private float rotation;
        private float rotationStep;
        private long lastUpdateTick;
        private int tickWait;

        public float getRandomlySpinningRotation(float partialTick) {
            return Mth.lerp(partialTick, this.lastRotation, this.rotation) * 360F;
        }

        public void update(long tickCount) {
            if (this.lastUpdateTick != tickCount) {
                this.updateRotation(tickCount);
            }
        }

        private void updateRotation(long tickCount) {
            this.lastUpdateTick = tickCount;
            this.lastRotation = this.rotation;

            if (this.rotation < this.targetRotation) {
                this.rotation += this.rotationStep;
                if (this.rotation > this.targetRotation) {
                    this.rotation = this.targetRotation;
                }
            } else if (this.rotation > this.targetRotation) {
                this.rotation -= this.rotationStep;
                if (this.rotation < this.targetRotation) {
                    this.rotation = this.targetRotation;
                }
            }

            // If we reached the target rotation, we need to set a new one
            // and calculate the stepping to get there.
            if (this.rotation == this.targetRotation) {
                if (this.tickWait < 0 ) {
                    this.tickWait = Randomizer.current().nextInt(TICK_DELAY);
                } else if (--this.tickWait == 0) {
                    this.targetRotation = Randomizer.current().nextFloat();
                    this.rotationStep = MAX_DELTA_TICK;
                }
            }
        }
    }
}
