// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * A ripple spreading where a drop lands on still water. Lies flat on the surface, just above it so the water doesn't
 * hide it from above.
 * <p>
 * It is drawn from one side only, as particles are, so seen from under the water it is turned to face down, and drawn
 * just below the surface, in front of the water's underside rather than behind it. From below it is dimmer, or it would
 * glow under the dark water.
 * <p>
 * Bigger ones are the rings where something strikes the water ({@link #createRing}): a waterfall, a body falling in.
 */
public class WaterRippleParticle extends SingleQuadParticle {

    // Lays the quad flat on the water, facing up, or facing down for seeing from below. extractRotatedQuad() only reads
    // them, so one of each serves every ripple.
    private static final Quaternionf FLAT = new Quaternionf().rotateX((float) Math.toRadians(-90f));
    private static final Quaternionf FLAT_FACING_DOWN = new Quaternionf().rotateX((float) Math.toRadians(90f));
    // Seen from below: how much lower than its place above the surface it is drawn, to be just under the surface
    // instead of just over it, and how bright and how opaque it is, as shares of what it is from above
    static final double UNDERSIDE_DROP = 0.025D;
    static final float UNDERSIDE_BRIGHTNESS = 0.8F;
    static final float UNDERSIDE_ALPHA = 0.7F;
    // How far above its place on the surface a ripple starts: the constructor lowers it, leaving it just over the water
    private static final double ABOVE_SURFACE = 0.21D;
    // How much of its drift a wake's ring keeps from one tick to the next
    static final double DRIFT_DECAY = 0.8D;
    // How much longer a wake's ring lasts than a ripple
    static final float WAKE_LINGER = 1.75F;

    private final WaterRippleStyle rippleStyle;
    private final SpriteSet spriteProvider;
    private final LifetimeAlpha lifetimeAlpha;

    private final float growthRate;
    // Its light, read once: it stays where it is, and is gone in a second
    private final int light;
    // A wake's ring: carried along, to start with, by what made it, in blocks a tick
    private double driftX, driftZ;
    // How far through its frames it starts, in ticks: a wake's ring skips those of a tiny ripple
    private int framesSkipped;

    public static Particle create(WaterRippleStyle rippleStyle, ClientLevel world, double x, double y, double z) {
        return new WaterRippleParticle(rippleStyle, world, x, y, z, 1F, DSurroundParticleSprites.forRippleStyle(rippleStyle));
    }

    /**
     * A ring on the water, spreading this many times as fast as a raindrop's
     *
     * @param surfaceY the height of the surface as it is drawn
     * @param lighten  how far its color is taken from the water's toward white, 0 to 1
     */
    public static Particle createRing(WaterRippleStyle rippleStyle, ClientLevel world, double x, double surfaceY, double z, float size, float lighten) {
        return ring(rippleStyle, world, x, surfaceY, z, size, lighten);
    }

    /**
     * A ring of something's wake: as big as what made it from the start, and carried along with it for a moment as it
     * spreads, or it is left behind before it looks like anything.
     *
     * @param driftX    how fast what made it is moving, in blocks a tick
     * @param startSize how big it starts, as a half width in blocks
     */
    public static Particle createWakeRing(WaterRippleStyle rippleStyle, ClientLevel world, double x, double surfaceY, double z, float size, float lighten,
                                          double driftX, double driftZ, float startSize) {
        var ring = ring(rippleStyle, world, x, surfaceY, z, size, lighten);
        ring.driftX = driftX;
        ring.driftZ = driftZ;
        ring.quadSize = startSize;
        ring.lifetime = (int) (ring.lifetime * WAKE_LINGER);
        ring.framesSkipped = ring.lifetime / 2;
        ring.setSprite(ring.spriteProvider.get(ring.framesSkipped, ring.lifetime + ring.framesSkipped));
        return ring;
    }

    private static WaterRippleParticle ring(WaterRippleStyle rippleStyle, ClientLevel world, double x, double surfaceY, double z, float size, float lighten) {
        var ring = new WaterRippleParticle(rippleStyle, world, x, surfaceY + ABOVE_SURFACE, z, size, DSurroundParticleSprites.forRippleStyle(rippleStyle));
        ring.rCol = Mth.lerp(lighten, ring.rCol, 1F);
        ring.gCol = Mth.lerp(lighten, ring.gCol, 1F);
        ring.bCol = Mth.lerp(lighten, ring.bCol, 1F);
        return ring;
    }

    protected WaterRippleParticle(WaterRippleStyle rippleStyle, ClientLevel world, double x, double y, double z, float size, SpriteSet spriteProvider) {
        super(world, x, y, z, 0.0, 0.0, 0.0, spriteProvider.first());

        this.rippleStyle = rippleStyle;
        this.spriteProvider = spriteProvider;
        this.lifetime = rippleStyle.getMaxAge();

        if (rippleStyle.doScaling()) {
            this.growthRate = size * this.lifetime / 500F;
            this.quadSize = this.growthRate;
        } else {
            this.growthRate = 0F;
            this.quadSize = 1F;
        }

        this.y -= 0.2D;

        var position = BlockPos.containing(this.x, this.y, this.z);
        var colorRgb = this.level.getBiome(position).value().getWaterColor();
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;

        if (this.rippleStyle.doAlpha()) {
            this.lifetimeAlpha = new LifetimeAlpha(0.9F, 0.0F, 0.0F, 1F);
        } else {
            this.lifetimeAlpha = LifetimeAlpha.ALWAYS_OPAQUE;
        }

        this.alpha = this.lifetimeAlpha.startAlpha();
        this.light = super.getLightCoords(0F);
        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public @NotNull Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void extract(final @NonNull QuadParticleRenderState particleTypeRenderState, final @NonNull Camera camera, final float tickDelta) {
        float alpha = this.lifetimeAlpha.currentAlphaForAge(this.age, this.lifetime, tickDelta);
        var cam = camera.position();
        double y = Mth.lerp(tickDelta, this.yo, this.y);
        float x = (float) (Mth.lerp(tickDelta, this.xo, this.x) - cam.x);
        float z = (float) (Mth.lerp(tickDelta, this.zo, this.z) - cam.z);

        if (!isSeenFromBelow(cam.y, y)) {
            this.setAlpha(alpha);
            this.extractRotatedQuad(particleTypeRenderState, FLAT, x, (float) (y - cam.y), z, tickDelta);
            return;
        }

        // From under the water: facing down, just below the surface, and dimmer
        float red = this.rCol, green = this.gCol, blue = this.bCol;
        this.rCol = red * UNDERSIDE_BRIGHTNESS;
        this.gCol = green * UNDERSIDE_BRIGHTNESS;
        this.bCol = blue * UNDERSIDE_BRIGHTNESS;
        this.setAlpha(alpha * UNDERSIDE_ALPHA);
        try {
            this.extractRotatedQuad(particleTypeRenderState, FLAT_FACING_DOWN, x, (float) (y - UNDERSIDE_DROP - cam.y), z, tickDelta);
        } finally {
            this.rCol = red;
            this.gCol = green;
            this.bCol = blue;
        }
    }

    /**
     * Whether the camera is below a ripple, so sees it from under the water
     */
    static boolean isSeenFromBelow(double cameraY, double rippleY) {
        return cameraY < rippleY;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return this.light;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (this.rippleStyle.doScaling()) {
            this.quadSize += this.growthRate;
        }

        this.x += this.driftX;
        this.z += this.driftZ;
        this.driftX *= DRIFT_DECAY;
        this.driftZ *= DRIFT_DECAY;

        this.setSprite(this.spriteProvider.get(this.age + this.framesSkipped, this.lifetime + this.framesSkipped));
    }
}
