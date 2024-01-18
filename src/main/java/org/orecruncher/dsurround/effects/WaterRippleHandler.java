package org.orecruncher.dsurround.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.particles.WaterRippleParticle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.tags.FluidTags;

public class WaterRippleHandler {

    private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);
    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);


    // Fudge factor because the height algo is off.
    private static final double LIQUID_HEIGHT_ADJUST = (1D / 9D) + 0.1D;

    private static boolean doRipples() {
        return CONFIG.waterRippleStyle != WaterRippleStyle.NONE;
    }

    private static void addWaterRipple(ClientLevel world, double x, double y, double z) {
        var ripple = new WaterRippleParticle(
                CONFIG.waterRippleStyle,
                world, x, y, z);
        GameUtils.getParticleManager().add(ripple);
    }

    public static void createRippleParticle(ClientLevel world, Particle particle, Vec3 position) {
        if (doRipples()) {
            var pos = BlockPos.containing(position);
            var fluidState = world.getFluidState(BlockPos.containing(position));

            if (fluidState.isSource() && TAG_LIBRARY.is(FluidTags.WATER_RIPPLES, fluidState)) {

                final float actualHeight = fluidState.getHeight(world, pos) + pos.getY();
                addWaterRipple(world, position.x, actualHeight + LIQUID_HEIGHT_ADJUST, position.z);

                // Expire the particle generated by Minecraft
                if (particle != null) {
                    particle.setPos(0, 0, 0);
                    particle.remove();
                }
            }
        }
    }
}
