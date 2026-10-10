package org.orecruncher.dsurround.effects.systems;

import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.effects.particles.WaterFoam;
import org.orecruncher.dsurround.effects.particles.WaterfallMist;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.function.Consumer;

/**
 * The mist and foam where a waterfall lands. With decreased particles, or far from the camera, there is half as much
 * (but always some).
 */
final class WaterfallSpray {

    // Mist puffs added each time particles are made, whatever the waterfall's strength
    static final int MIST_PUFFS = 4;

    private WaterfallSpray() {
    }

    /**
     * How many mist puffs to add: the same for every waterfall, as there is mist wherever one lands; a bigger
     * waterfall makes bigger puffs and churns them harder, rather than making more.
     */
    static int mistCount(ParticleStatus status, boolean far) {
        return reduced(MIST_PUFFS, status, far);
    }

    /**
     * How many foam patches to add: more for a bigger waterfall.
     */
    static int foamCount(int strength, ParticleStatus status, boolean far) {
        return reduced(2 + strength / 3, status, far);
    }

    /**
     * How fast foam is thrown out, at the least: faster for a bigger waterfall. The most is 0.06 more.
     */
    static double foamMinSpeed(int strength) {
        return 0.06D + strength * 0.01D;
    }

    private static int reduced(int count, ParticleStatus status, boolean far) {
        return status != ParticleStatus.ALL || far ? Math.max(1, count / 2) : count;
    }

    /**
     * Adds foam patches on the water around where the waterfall lands (see {@link WaterFoam#spawnAround}), thrown out
     * from it: carried off by the water spreading from the impact, or coasting out across a pool.
     *
     * @param far whether the waterfall is far from the camera
     */
    static void addFoam(ClientLevel level, BlockPos position, int strength, ParticleStatus status, boolean far,
                        IRandomizer random, Consumer<Particle> particles) {
        double minSpeed = foamMinSpeed(strength);
        WaterFoam.spawnAround(level, position, foamCount(strength, status, far), minSpeed, minSpeed + 0.06D, random, particles);
    }

    /**
     * Adds a few mist puffs (see {@link WaterfallMist}): scattered around where the water lands, thrown up and out
     * from it. Puffs live 1 to 3 seconds, and this runs every few ticks, so a waterfall has about 40 at a time.
     *
     * @param x        the middle of where the waterfall lands
     * @param surfaceY the visible surface of the water there
     * @param far      whether the waterfall is far from the camera
     */
    static void addMist(ClientLevel level, double x, double surfaceY, double z, int strength, ParticleStatus status,
                        boolean far, IRandomizer random, Consumer<Particle> particles) {
        int count = mistCount(status, far);

        // Thrown harder by a bigger waterfall
        final double outwardScale = 1D + strength * 0.1D;
        final double upwardScale = 1D + strength * 0.08D;

        for (int i = 0; i < count; i++) {
            // A direction out from the impact, and how far from its center the puff starts
            double angle = random.nextDouble() * Mth.TWO_PI;
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            double radius = random.nextDouble() * 0.6D;

            double outward = (0.025D + random.nextDouble() * 0.05D) * outwardScale;
            double upward = (0.02D + random.nextDouble() * 0.03D) * upwardScale;

            // Starts a little under the surface and rises out of it; the soft shader hides the part under the water
            double depth = 0.25D + random.nextDouble() * 0.25D;

            var puff = WaterfallMist.create(level,
                    x + dirX * radius, surfaceY - depth, z + dirZ * radius,
                    dirX * outward, upward, dirZ * outward,
                    x, surfaceY, z,
                    strength);
            particles.accept(puff);
        }
    }
}
