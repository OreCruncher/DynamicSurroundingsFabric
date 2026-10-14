package org.orecruncher.dsurround.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.CompassStyle;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.tags.ItemEffectTags;

/**
 * Shows a compass above the crosshair while a compass is held: a band that slides with the heading, or a rose
 * laid flat in front of the player that turns with it.
 */
public final class CompassOverlay extends AbstractOverlay {

    // Vertical offset to avoid writing over the cross-hair
    private static final int CROSSHAIR_OFFSET = 60;

    // The texture is 512x512. Each style has two rows of bands: the first half of the heading on the first, the
    // second half on the second.
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

    private final ITagLibrary tagLibrary;
    private final IDimensionInformation dimensionInformation;
    private final Configuration config;
    private final CompassWobble wobbler;
    private boolean showCompass;
    private boolean spinRandomly;
    private float scale;
    private CompassStyle style;

    public CompassOverlay(Configuration config, ITagLibrary tagLibrary, IDimensionInformation dimensionInformation) {
        this.tagLibrary = tagLibrary;
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

        this.style = this.config.compassAndClockOptions.compassStyle;
        if (this.style.isShown() && GameUtils.isInGame()) {
            this.scale = (float) this.config.compassAndClockOptions.scale;

            var player = GameUtils.getPlayer().orElseThrow();
            var mainHandItem = player.getMainHandItem();
            var offHandItem = player.getOffhandItem();

            var mainHandShow = this.doShowCompass(mainHandItem);
            var offHandShow = this.doShowCompass(offHandItem);
            this.showCompass = mainHandShow || offHandShow;
            this.spinRandomly = shouldSpin(mainHandShow, mainHandShow && this.doCompassSpin(mainHandItem),
                    offHandShow, offHandShow && this.doCompassSpin(offHandItem));

            this.wobbler.update(player.level().getGameTime());
        }
    }

    /**
     * Whether the compass band spins randomly: if a compass held in either hand is one that wobbles (in a dimension
     * where compasses wobble). Decided afresh each tick from what is held now.
     */
    static boolean shouldSpin(boolean mainHandCompass, boolean mainHandWobbles, boolean offHandCompass, boolean offHandWobbles) {
        return (mainHandCompass && mainHandWobbles) || (offHandCompass && offHandWobbles);
    }

    /**
     * Whether the compass is shown this tick: the pocket watch moves aside for it
     */
    public boolean isShowing() {
        return this.showCompass;
    }

    /**
     * Half the compass's width on screen, from the center of the screen
     */
    public float halfWidth() {
        return (this.style.isRose() ? ROSE_SIZE * ROSE_DRAW_SCALE : BAND_WIDTH) * this.scale / 2F;
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

        // The transparent styles and the roses need blending; this version's blit doesn't set it up itself
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (this.style.isRose())
            this.renderRose(context, rotation);
        else
            this.renderBand(context, rotation);
        RenderSystem.disableBlend();
    }

    private void renderBand(GuiGraphics context, float rotation) {
        // Which part of the band to show: the heading, as a position across the texture
        int u = Mth.floor(((rotation * TEXTURE_SIZE) / 360F) + 0.5D) & (TEXTURE_SIZE - 1);
        float v = this.style.getSpriteNumber() * (BAND_HEIGHT * 2);
        if (u >= HALF_TEXTURE_SIZE) {
            u -= HALF_TEXTURE_SIZE;
            v += BAND_HEIGHT;
        }

        // Centered horizontally, above the crosshair, at the configured scale
        float x = (context.guiWidth() - BAND_WIDTH * this.scale) / 2F;
        float y = (context.guiHeight() - CROSSHAIR_OFFSET - BAND_HEIGHT * this.scale) / 2F;

        var pose = context.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0F);
            pose.scale(this.scale, this.scale, 1F);
            context.blit(this.style.getTexture(), 0, 0, u, v, BAND_WIDTH, BAND_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        } finally {
            pose.popPose();
        }
    }

    private void renderRose(GuiGraphics context, float rotation) {
        // Centered where the band's center is, above the crosshair
        float x = context.guiWidth() / 2F;
        float y = (context.guiHeight() - CROSSHAIR_OFFSET) / 2F;
        float size = this.scale * ROSE_DRAW_SCALE;

        var pose = context.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0F);
            pose.scale(size, size * ROSE_TILT, 1F);
            pose.mulPose(Axis.ZP.rotationDegrees(roseAngle(rotation)));
            context.blit(this.style.getTexture(), -ROSE_SIZE / 2, -ROSE_SIZE / 2, 0, 0, ROSE_SIZE, ROSE_SIZE, ROSE_SIZE, ROSE_SIZE);
        } finally {
            pose.popPose();
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
