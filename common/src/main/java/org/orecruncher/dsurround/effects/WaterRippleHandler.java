// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.WaterRippleParticle;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.tags.FluidTags;

import java.util.Optional;

public class WaterRippleHandler {

    private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);
    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private static final boolean compatibleEnvironment = !FeatureConflict.RAIN_RIPPLES.isBlocked();

    // Fudge factor because the height algo is off.
    private static final double LIQUID_HEIGHT_ADJUST = (1D / 9D) + 0.1D;

    public static boolean doRipples() {
        return compatibleEnvironment && CONFIG.waterRippleStyle != WaterRippleStyle.NONE;
    }

    /**
     * A ring on the water where something strikes it, if ripples are on
     *
     * @param surfaceY the height of the surface as it is drawn
     * @param size     how many times as fast as a raindrop's it spreads
     */
    public static Optional<Particle> createRing(ClientLevel world, double x, double surfaceY, double z, float size) {
        if (!doRipples())
            return Optional.empty();
        return Optional.of(WaterRippleParticle.createRing(CONFIG.waterRippleStyle, world, x, surfaceY, z, size));
    }

    /**
     * A ripple where spray comes down on water, if ripples are on: on the surface, so not under falling water, where
     * the fluid's height is the block's and above the surface that is drawn
     */
    public static Optional<Particle> createSprayRipple(ClientLevel world, double x, double y, double z) {
        if (!world.getFluidState(BlockPos.containing(x, y, z).above()).isEmpty())
            return Optional.empty();
        return createRippleParticle(world, new Vec3(x, y, z));
    }

    public static Optional<Particle> createRippleParticle(ClientLevel world, Vec3 position) {
        if (doRipples()) {
            var pos = BlockPos.containing(position);
            var fluidState = world.getFluidState(pos);

            if (fluidState.isSource() && TAG_LIBRARY.is(FluidTags.WATER_RIPPLES, fluidState)) {
                final float actualHeight = fluidState.getHeight(world, pos) + pos.getY();
                return Optional.of(WaterRippleParticle.create(CONFIG.waterRippleStyle, world, position.x, actualHeight + LIQUID_HEIGHT_ADJUST, position.z));
            }
        }
        return Optional.empty();
    }
}
