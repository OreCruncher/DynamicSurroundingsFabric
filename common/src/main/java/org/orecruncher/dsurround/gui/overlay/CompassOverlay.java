package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.tags.ItemEffectTags;

public class CompassOverlay extends AbstractOverlay {

    // Vertical offset to avoid writing over the cross-hair
    private static final int CROSSHAIR_OFFSET = 60;

    // Width and height of the actual band in the texture. The texture is 512x512 but the actual
    // rendering is smaller.
    private static final int TEXTURE_SIZE = 512;
    private static final int BAND_WIDTH = 65 * 2;
    private static final int BAND_HEIGHT = 12 * 2;
    private static final int HALF_TEXTURE_SIZE = TEXTURE_SIZE / 2;
    private static final Identifier COMPASS_TEXTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/compass.png");

    private final ITagLibrary tagLibrary;
    private final IDimensionInformation dimensionInformation;
    private final Configuration config;
    private final CompassWobble wobbler;
    private boolean showCompass;
    private boolean spinRandomly;
    private float scale;
    private float spriteOffset;

    public CompassOverlay(Configuration config, ITagLibrary tagLibrary, IDimensionInformation dimensionInformation) {
        this.tagLibrary = tagLibrary;
        this.dimensionInformation = dimensionInformation;
        this.config = config;
        this.wobbler = new CompassWobble();
        this.showCompass = false;
        this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();
        this.scale = (float)this.config.compassAndClockOptions.scale;
    }

    public void tick(Minecraft client) {
        this.showCompass = false;

        if (this.config.compassAndClockOptions.enableCompass && GameUtils.isInGame()) {
            this.scale = (float) this.config.compassAndClockOptions.scale;
            this.spriteOffset = this.config.compassAndClockOptions.compassStyle.getSpriteNumber();

            var player = GameUtils.getPlayer().orElseThrow();
            var mainHandItem = player.getMainHandItem();
            var offHandItem = player.getOffhandItem();

            var mainHandShow = this.doShowCompass(mainHandItem);
            var offHandShow = this.doShowCompass(offHandItem);
            this.showCompass = mainHandShow || offHandShow;

            if (mainHandShow) {
                this.spinRandomly = this.doCompassSpin(mainHandItem);
            }

            if (offHandShow && !this.spinRandomly) {
                this.spinRandomly = this.doCompassSpin(offHandItem);
            }

            this.wobbler.update(player.level().getGameTime());
        }
    }

    private boolean doShowCompass(ItemStack stack) {
        return this.tagLibrary.is(ItemEffectTags.COMPASSES, stack);
    }

    private boolean doCompassSpin(ItemStack stack) {
        return this.dimensionInformation.getCompassWobble() && this.tagLibrary.is(ItemEffectTags.COMPASS_WOBBLE, stack);
    }

    @Override
    public void render(GuiGraphicsExtractor context, float partialTick) {
        if (!this.showCompass)
            return;

        var matrices = context.pose();

        try {
            matrices.pushMatrix();

            float rotation;
            if (this.spinRandomly) {
                rotation = this.wobbler.getRandomlySpinningRotation(partialTick);
            } else {
                final var player = GameUtils.getPlayer().orElseThrow();
                rotation = player.getViewYRot(partialTick);
            }

            int direction = Mth.floor(((rotation * TEXTURE_SIZE) / 360F) + 0.5D) & (TEXTURE_SIZE - 1);
            float x = (context.guiWidth() - BAND_WIDTH * this.scale) / 2F;
            float y = (context.guiHeight() - CROSSHAIR_OFFSET - BAND_HEIGHT * this.scale) / 2F;

            matrices.scale(this.scale, this.scale);
            x /= this.scale;
            y /= this.scale;

            int v = Mth.floor(this.spriteOffset * (BAND_HEIGHT * 2));
            if (direction >= HALF_TEXTURE_SIZE) {
                direction -= HALF_TEXTURE_SIZE;
                v += BAND_HEIGHT;
            }

            context.blit(
                    RenderPipelines.GUI_TEXTURED,
                    COMPASS_TEXTURE,
                    Mth.floor(x),
                    Mth.floor(y),
                    direction,
                    v,
                    BAND_WIDTH,
                    BAND_HEIGHT,
                    BAND_WIDTH,
                    BAND_HEIGHT,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE);
        } finally {
            matrices.popMatrix();
        }
    }

    /**
     * Cloned from the Minecraft compass code
     */
    static class CompassWobble {
        private static int TICK_DELAY = 5;
        private static float MAX_DELTA_TICK = 1F / 20F;
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
