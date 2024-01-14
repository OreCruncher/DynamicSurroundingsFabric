package org.orecruncher.dsurround.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.effects.particles.WaterRippleParticle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;

public class WaterRippleHandler {

    private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);

    // Fudge factor because the height algo is off.
    private static final double LIQUID_HEIGHT_ADJUST = (1D / 9D) + 0.1D;

    // Hook for Rain particle effect to generate a ripple instead of a splash
    public static void spawnRippleOnBlock(final ClientLevel world, final Vec3 position) {
        final BlockPos pos = BlockPos.containing(position.x, position.y - 0.01D, position.z);
        final FluidState fluidState = world.getFluidState(pos);
        if (fluidState.isEmpty() || !fluidState.isSource()) return;

        final float actualHeight = fluidState.getHeight(world, pos) + pos.getY();
        addWaterRipple(world, position.x, actualHeight + LIQUID_HEIGHT_ADJUST, position.z);
    }

    private static boolean doRipples() {
        return CONFIG.waterRippleStyle != WaterRippleStyle.NONE;
    }

    private static void addWaterRipple(ClientLevel world, double x, double y, double z) {
        var ripple = new WaterRippleParticle(
                CONFIG.waterRippleStyle,
                world, x, y, z);
        GameUtils.getParticleManager().add(ripple);
    }

    public static boolean createRippleParticle(ClientLevel world, Particle particle, Vec3 position) {
        if (!doRipples())
            return false;

        WaterRippleHandler.spawnRippleOnBlock(world, position);

        // Expire the particle generated by Minecraft rather than returning null.
        if (particle != null) {
            particle.setPos(0, 0, 0);
            particle.remove();
        }
        return true;
    }
}
