// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * A patch of foam at the front of a wave on a beach: it slides up over the water's edge and onto the shore, then back,
 * fading as it goes, lying on whatever is under it. Unlike {@link WaterFoam} it isn't carried by the water, and it can
 * cross dry ground. Drawn as an ordinary translucent particle, flat, as foam is.
 */
public class ShoreFoam extends SingleQuadParticle {

    private static final Quaternionf FLAT = new Quaternionf().rotateX((float) Math.toRadians(-90f));
    // How far above what it lies on, so it isn't hidden by it
    private static final double LIFT = 0.03D;
    // How quickly it settles to the height of what is under it
    private static final double SETTLE = 0.2D;
    // How far the ground can rise above the water it started on before it stops at it, in blocks: enough for a shore
    // level with the water, and no block higher
    private static final double MAX_RISE = 0.5D;
    // How far toward white the biome's water color is shifted
    private static final float WHITENESS = 0.9F;
    private static final float PEAK_ALPHA = 0.8F;

    private final double startX, startZ, startY;
    private final double reachX, reachZ;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private float fade;
    // The furthest it can go, 0 to 1 of its reach: lowered if it comes to a rise
    private float furthest = 1F;
    private float along;

    /**
     * @param surfaceY the surface of the water where it starts
     * @param dirX     the way up the shore, along X, as part of a unit vector
     * @param dirZ     along Z
     * @param reach    how far up the shore it goes, in blocks
     * @param lifetime how long the wave takes, up and back, in ticks
     */
    public static Particle create(ClientLevel level, double x, double surfaceY, double z, double dirX, double dirZ, double reach, int lifetime) {
        return new ShoreFoam(level, x, surfaceY, z, dirX, dirZ, reach, lifetime);
    }

    private ShoreFoam(ClientLevel level, double x, double y, double z, double dirX, double dirZ, double reach, int lifetime) {
        super(level, x, y + LIFT, z, DSurroundParticleSprites.WATER_FOAM.get(level.getRandom()));
        this.startX = x;
        this.startZ = z;
        this.startY = y;
        this.reachX = dirX * reach;
        this.reachZ = dirZ * reach;
        this.lifetime = lifetime;
        this.hasPhysics = false;
        this.gravity = 0F;
        this.xd = this.yd = this.zd = 0D;
        var random = level.getRandom();
        this.quadSize = 0.2F + random.nextFloat() * 0.2F;
        this.alpha = 0F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;

        var biomeColor = level.getBiome(BlockPos.containing(x, y, z)).value().getWaterColor();
        var colorRgb = ARGB.srgbLerp(WHITENESS, biomeColor, ColorPalette.MC_WHITE.getValue());
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;
    }

    @Override
    public @NotNull SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
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

        float life = (float) this.age / this.lifetime;
        this.fade = alphaAt(life);
        float next = Math.min(reachAt(life), this.furthest);
        double x = this.startX + this.reachX * next;
        double z = this.startZ + this.reachZ * next;
        double ground = this.groundAt(x, z);
        if (ground > this.startY + MAX_RISE) {
            // A rise: it goes no further up the shore, and waits there to go back
            this.furthest = this.along;
            next = Math.min(next, this.along);
            x = this.startX + this.reachX * next;
            z = this.startZ + this.reachZ * next;
            ground = this.groundAt(x, z);
        }
        this.along = next;
        this.setPos(x, Mth.lerp(SETTLE, this.y, ground + LIFT), z);
    }

    /**
     * The height of what foam lies on here: the water's surface, or the top of the ground
     */
    private double groundAt(double x, double z) {
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        this.probe.set(x, top - 1, z);
        var fluid = this.level.getFluidState(this.probe);
        if (!fluid.isEmpty())
            return this.probe.getY() + fluid.getHeight(this.level, this.probe);
        var shape = this.level.getBlockState(this.probe).getShape(this.level, this.probe);
        return this.probe.getY() + (shape.isEmpty() ? 0D : shape.max(Direction.Axis.Y));
    }

    @Override
    public void extract(final @NonNull QuadParticleRenderState state, final @NonNull Camera camera, final float tickDelta) {
        this.setAlpha(this.fade * PEAK_ALPHA);
        var cam = camera.position();
        this.extractRotatedQuad(state, FLAT, (float) (Mth.lerp(tickDelta, this.xo, this.x) - cam.x),
                (float) (Mth.lerp(tickDelta, this.yo, this.y) - cam.y), (float) (Mth.lerp(tickDelta, this.zo, this.z) - cam.z), tickDelta);
    }

    /**
     * How far up the shore it is, 0 to 1, a share of the way through its life: quickly up, slowing to the top, and back
     */
    static float reachAt(float life) {
        return Mth.sin(Mth.PI * Math.min(1F, life));
    }

    /**
     * How strong it is a share of the way through its life: in gradually as it rolls in, thinning from the top of the
     * wave on
     */
    static float alphaAt(float life) {
        return Math.min(1F, life * 2.5F) * (1F - life * life);
    }
}
