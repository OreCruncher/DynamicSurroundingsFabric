package org.orecruncher.dsurround.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.ParticleUtils;
import org.orecruncher.dsurround.effects.particles.WaterRippleParticle;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.tags.FluidTags;

public class WaterRippleHandler {

    // Fudge factor because the fluid-height algorithm reports the top surface a little low.
    private static final double LIQUID_HEIGHT_ADJUST = (1D / 9D) + 0.1D;

    private static Configuration.BlockEffects config() {
        return ContainerManager.resolve(Configuration.BlockEffects.class);
    }

    private static ITagLibrary tags() {
        return ContainerManager.resolve(ITagLibrary.class);
    }

    private static boolean doRipples(Configuration.BlockEffects config) {
        return config.waterRippleStyle != WaterRippleStyle.NONE;
    }

    private static void addWaterRipple(WaterRippleStyle style, ClientLevel world, double x, double y, double z) {
        ParticleUtils.addParticle(WaterRippleParticle.create(style, world, x, y, z));
    }

    public static void createRippleParticle(ClientLevel world, Particle particle, Vec3 position) {
        var config = config();
        if (!doRipples(config)) {
            return;
        }

        var pos = BlockPos.containing(position);
        var fluidState = world.getFluidState(pos);
        if (fluidState.isSource() && tags().is(FluidTags.WATER_RIPPLES, fluidState)) {
            final float actualHeight = fluidState.getHeight(world, pos) + pos.getY();
            addWaterRipple(config.waterRippleStyle, world, position.x, actualHeight + LIQUID_HEIGHT_ADJUST, position.z);

            // Replace the vanilla drop with the Dynamic Surroundings water wake.
            if (particle != null) {
                particle.setPos(0, 0, 0);
                particle.remove();
            }
        }
    }
}
