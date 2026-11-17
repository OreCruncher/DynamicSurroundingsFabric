// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.WaterRippleParticle;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.tags.FluidTags;

import java.util.Optional;
import java.util.OptionalDouble;

public class WaterRippleHandler {

    private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);
    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private static final boolean compatibleEnvironment = !FeatureConflict.RAIN_RIPPLES.isBlocked();

    // Fudge factor because the height algo is off.
    private static final double LIQUID_HEIGHT_ADJUST = (1D / 9D) + 0.1D;
    // How much lighter rings from something floating or wading are, 0 to 1: see createRing
    public static final float FLOATING_RING_LIGHTEN = 0.35F;
    // How far up from a point to look for the surface of the water it is in
    private static final int SURFACE_SEARCH = 3;

    public static boolean doRipples() {
        return compatibleEnvironment && doRings();
    }

    /**
     * Whether rings are drawn where something strikes or moves through the water. Unlike rain's ripples they don't give
     * way to a mod that makes its own: it has no rings of this kind.
     */
    public static boolean doRings() {
        return CONFIG.waterRippleStyle != WaterRippleStyle.NONE;
    }

    /**
     * A ring on the water where something strikes it, if ripples are on
     *
     * @param surfaceY the height of the surface as it is drawn
     * @param size     how many times as fast as a raindrop's it spreads
     */
    public static Optional<Particle> createRing(ClientLevel world, double x, double surfaceY, double z, float size) {
        return createRing(world, x, surfaceY, z, size, 0F);
    }

    /**
     * A ring on the water, as {@link #createRing(ClientLevel, double, double, double, float)}, lighter: a small ring
     * from something floating needs it to show
     *
     * @param lighten how far its color is taken from the water's toward white, 0 to 1
     */
    public static Optional<Particle> createRing(ClientLevel world, double x, double surfaceY, double z, float size, float lighten) {
        if (!doRings())
            return Optional.empty();
        return Optional.of(WaterRippleParticle.createRing(CONFIG.waterRippleStyle, world, x, surfaceY, z, size, lighten));
    }

    /**
     * The height of the surface of still water at a point in it or just above it; empty if there is none, or it is
     * flowing or doesn't ripple
     */
    public static OptionalDouble stillSurface(Level world, double x, double y, double z) {
        return stillSurface(world, x, y, z, SURFACE_SEARCH);
    }

    /**
     * As {@link #stillSurface(Level, double, double, double)}, looking up to {@code search} blocks up for the surface
     */
    public static OptionalDouble stillSurface(Level world, double x, double y, double z, int search) {
        var pos = BlockPos.containing(x, y, z).mutable();
        if (world.getFluidState(pos).isEmpty())
            pos.move(Direction.DOWN);
        for (int i = 0; i < search && !world.getFluidState(pos.above()).isEmpty(); i++)
            pos.move(Direction.UP);
        var fluid = world.getFluidState(pos);
        if (!fluid.isSource() || !world.getFluidState(pos.above()).isEmpty() || !TAG_LIBRARY.is(FluidTags.WATER_RIPPLES, fluid))
            return OptionalDouble.empty();
        return OptionalDouble.of(pos.getY() + fluid.getHeight(world, pos));
    }

    /**
     * A ripple where spray comes down on water, if ripples are on: on the surface, so not under falling water, where
     * the fluid's height is the block's and above the surface that is drawn
     */
    public static Optional<Particle> createSprayRipple(ClientLevel world, double x, double y, double z) {
        if (!world.getFluidState(BlockPos.containing(x, y, z).above()).isEmpty())
            return Optional.empty();
        return doRings() ? createRipple(world, new Vec3(x, y, z)) : Optional.empty();
    }

    /**
     * A ring of something's wake, if ripples are on: see {@link WaterRippleParticle#createWakeRing}
     */
    public static Optional<Particle> createWakeRing(ClientLevel world, double x, double surfaceY, double z, float size,
                                                    double driftX, double driftZ, float startSize) {
        if (!doRings())
            return Optional.empty();
        return Optional.of(WaterRippleParticle.createWakeRing(CONFIG.waterRippleStyle, world, x, surfaceY, z, size,
                FLOATING_RING_LIGHTEN, driftX, driftZ, startSize));
    }

    public static Optional<Particle> createRippleParticle(ClientLevel world, Vec3 position) {
        return doRipples() ? createRipple(world, position) : Optional.empty();
    }

    private static Optional<Particle> createRipple(ClientLevel world, Vec3 position) {
        var pos = BlockPos.containing(position);
        var fluidState = world.getFluidState(pos);
        if (!fluidState.isSource() || !TAG_LIBRARY.is(FluidTags.WATER_RIPPLES, fluidState))
            return Optional.empty();
        final float actualHeight = fluidState.getHeight(world, pos) + pos.getY();
        return Optional.of(WaterRippleParticle.create(CONFIG.waterRippleStyle, world, position.x, actualHeight + LIQUID_HEIGHT_ADJUST, position.z));
    }
}
