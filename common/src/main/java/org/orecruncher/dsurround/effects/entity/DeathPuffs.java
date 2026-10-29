package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.blocks.AbstractBlockEffect;
import org.orecruncher.dsurround.effects.particles.DeathMist;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * Dark mist where a mob's body vanishes at the end of its death, in place of vanilla's white poof (20 {@code POOF}
 * particles from {@code LivingEntity.makePoofParticles}, hooked in {@code MixinLivingEntity}). Vanilla's stays with
 * Particles on Minimal.
 */
public final class DeathPuffs {

    // Holder, so the class can be loaded (and tested) without the container
    private static final class ConfigHolder {
        static final Configuration.EntityEffects CONFIG = ContainerManager.resolve(Configuration.EntityEffects.class);
    }

    private static final IRandomizer RANDOM = Randomizer.current();

    // Puffs per square block of the body's side (width times height): about 8 for a zombie
    static final float PUFFS_PER_AREA = 7F;
    static final int MIN_PUFFS = 3;
    static final int MAX_PUFFS = 16;
    // Puff size for a body of this width: a zombie's (0.6) gets 1
    private static final float NORMAL_WIDTH = 0.6F;
    // How far from the camera vanilla shows particles; past it there is nothing to replace
    private static final double PARTICLE_RANGE_SQ = (double) AbstractBlockEffect.VANILLA_PARTICLE_RANGE * AbstractBlockEffect.VANILLA_PARTICLE_RANGE;
    // How fast puffs leave the body, in blocks a tick, and how much they rise
    private static final double BURST = 0.05D;
    private static final double RISE = 0.015D;

    private DeathPuffs() {
    }

    /**
     * Makes the mist for {@code entity}'s death, if it is to replace vanilla's.
     *
     * @return true if vanilla's poof is to be left out
     */
    public static boolean replace(LivingEntity entity) {
        if (!ConfigHolder.CONFIG.enableDeathPuffs || !(entity.level() instanceof ClientLevel level))
            return false;
        var particles = GameUtils.getGameSettings().particles().get();
        if (particles == ParticleStatus.MINIMAL)
            return false;

        // Particles go straight to the particle engine, which skips vanilla's distance check, so apply it here
        var camera = GameUtils.getMC().gameRenderer.getMainCamera().position();
        if (camera.distanceToSqr(entity.getX(), entity.getY(), entity.getZ()) > PARTICLE_RANGE_SQ)
            return true;

        final float width = entity.getBbWidth();
        final float height = entity.getBbHeight();
        int puffs = puffsFor(width, height);
        if (particles == ParticleStatus.DECREASED)
            puffs = Math.max(MIN_PUFFS, puffs / 2);
        final float scale = Mth.clamp(Mth.sqrt(width / NORMAL_WIDTH), 0.7F, 2F);
        final double centerY = entity.getY() + height * 0.5D;

        var engine = GameUtils.getParticleManager();
        for (int i = 0; i < puffs; i++) {
            // Where vanilla puts its poof: anywhere in the body's box
            double x = entity.getRandomX(1D);
            double y = entity.getRandomY();
            double z = entity.getRandomZ(1D);
            // Out from the middle of the body, a little stirred
            double dx = x - entity.getX();
            double dy = y - centerY;
            double dz = z - entity.getZ();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double out = length > 1.0E-4D ? BURST / length : 0D;
            engine.add(DeathMist.create(level, x, y, z,
                    dx * out + RANDOM.nextGaussian() * 0.01D,
                    dy * out * 0.5D + RISE + RANDOM.nextGaussian() * 0.005D,
                    dz * out + RANDOM.nextGaussian() * 0.01D,
                    scale));
        }
        return true;
    }

    /**
     * Puffs for a body of this size
     */
    static int puffsFor(float width, float height) {
        return Mth.clamp(Math.round(PUFFS_PER_AREA * width * height), MIN_PUFFS, MAX_PUFFS);
    }
}
