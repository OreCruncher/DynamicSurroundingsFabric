package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.particles.FlameSpark;
import org.orecruncher.dsurround.effects.particles.FlameTongue;
import org.orecruncher.dsurround.effects.particles.LavaDroplet;
import org.orecruncher.dsurround.effects.particles.SmokePuff;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FlameJetEffect extends AbstractParticleEmitterEffect {

    private static final Identifier FIRE_AMBIENT = Constants.asId("fire_ambient");

    protected final boolean isLava;
    protected final boolean isNonLiquidBlock;
    protected boolean soundFired;

    // Emits every other tick: the tongues of flame are short-lived
    private static final int EMIT_TICKS = 2;
    // Tongues at a time: this many, and one more for each block of strength, at most this many
    private static final int MIN_TONGUES = 2;
    private static final int MAX_TONGUES = 10;
    // The chance each time it emits that a puff of smoke rises from above the flames, at full burn
    private static final float SMOKE_CHANCE = 0.6F;
    // The chance each time it emits that sparks fly, at full burn, and how many at most
    private static final float SPARK_CHANCE = 0.35F;
    private static final int MAX_SPARKS = 2;
    // A lava jet: how far from its middle droplets are thrown up, how fast sideways, and the chance each time it
    // emits of a vanilla lava pop, at full burn
    private static final double DROPLET_SPREAD = 0.15D;
    private static final double DROPLET_SCATTER = 0.035D;
    private static final float LAVA_POP_CHANCE = 0.08F;

    public FlameJetEffect(final int strength, final Level world, final double x, final double y, final double z, boolean isNonLiquidBlock) {
        super(strength, world, x, y, z, EMIT_TICKS);
        this.isLava = !isNonLiquidBlock && RANDOM.nextInt(3) == 0;
        this.isNonLiquidBlock = isNonLiquidBlock;
    }

    @Override
    protected void soundUpdate() {
        if (!this.soundFired) {
            this.soundFired = true;
            if (this.strength > 1) {
                SOUND_LIBRARY.getSoundFactory(FIRE_AMBIENT)
                        .ifPresent(f -> {
                            var soundInstance = f.createAtLocation(getPos());
                            AUDIO_PLAYER.play(soundInstance);
                        });
            }
        }
    }

    @Override
    protected Collection<Particle> produceParticles() {
        if (!(this.world instanceof ClientLevel level))
            return List.of();

        // Fewer with the Particles setting lowered; on Minimal just a flame or two and no smoke
        var status = GameUtils.getGameSettings().particles().get();
        float share = status == ParticleStatus.ALL ? 1F : status == ParticleStatus.DECREASED ? 0.5F : 0.2F;
        float burn = FlameJetBurn.burn(this.age, this.ageLimit);
        if (burn <= 0F)
            return List.of();
        if (this.isLava)
            return this.lavaJet(level, burn, share);

        // A small source (a single block that isn't a full one) burns smaller and lower
        boolean small = this.isNonLiquidBlock && this.strength == 1;
        float scale = (small ? 0.5F : 1F) * (1F + 0.25F * (this.strength - 1));
        double speed = this.strength / 10.0D * (small ? 0.5D : 1D);
        double spread = this.isNonLiquidBlock ? 0.25D : 0.12D + 0.03D * this.strength;

        int tongues = Math.max(1, Math.round(Math.min(MAX_TONGUES, MIN_TONGUES + this.strength) * burn * share));
        var particles = new ArrayList<Particle>(tongues + 1 + MAX_SPARKS);
        for (int i = 0; i < tongues; i++) {
            double x = this.posX + RANDOM.nextGaussian() * spread;
            double z = this.posZ + RANDOM.nextGaussian() * spread;
            // Weaker as it flares and as it dies down, as well as fewer
            double rise = speed * (0.8D + RANDOM.nextDouble() * 0.4D) * (0.5D + 0.5D * burn);
            double sideways = 0.01D;
            // The first of each batch carries the jet's light: a handful alive at once, rising and fading
            particles.add(FlameTongue.create(level, x, this.posY, z,
                    (RANDOM.nextDouble() - 0.5D) * sideways, rise, (RANDOM.nextDouble() - 0.5D) * sideways, scale, i == 0));
        }

        // Now and then sparks fly up out of the flames, and arc over
        if (status != ParticleStatus.MINIMAL && RANDOM.nextFloat() < SPARK_CHANCE * burn * share) {
            int sparks = 1 + RANDOM.nextInt(MAX_SPARKS);
            for (int i = 0; i < sparks; i++) {
                double x = this.posX + RANDOM.nextGaussian() * spread;
                double z = this.posZ + RANDOM.nextGaussian() * spread;
                double up = 0.15D + speed * 0.6D + RANDOM.nextDouble() * 0.15D;
                double scatter = 0.06D;
                particles.add(FlameSpark.create(level, x, this.posY + 0.2D, z,
                        (RANDOM.nextDouble() - 0.5D) * scatter, up, (RANDOM.nextDouble() - 0.5D) * scatter));
            }
        }

        // Smoke from above the flames, where they burn out
        if (status != ParticleStatus.MINIMAL && RANDOM.nextFloat() < SMOKE_CHANCE * burn * share) {
            double top = this.posY + 0.3D + speed * 8D;
            double x = this.posX + RANDOM.nextGaussian() * spread;
            double z = this.posZ + RANDOM.nextGaussian() * spread;
            particles.add(SmokePuff.create(level, x, top, z, 0.03D + speed * 0.3D, scale));
        }
        return particles;
    }

    /**
     * Lava throws up droplets of molten rock that arc over and fall back, more the deeper the lava, and now and then a
     * pop as vanilla's lava does
     */
    private Collection<Particle> lavaJet(ClientLevel level, float burn, float share) {
        int droplets = FlameJetBurn.droplets(this.strength, burn, share, RANDOM.nextFloat(), RANDOM.nextFloat());
        boolean pop = RANDOM.nextFloat() < LAVA_POP_CHANCE * burn;
        if (droplets == 0 && !pop)
            return List.of();

        var particles = new ArrayList<Particle>(droplets + 1);
        for (int i = 0; i < droplets; i++) {
            double up = FlameJetBurn.dropletSpeed(this.strength, burn, RANDOM.nextFloat());
            double x = this.posX + RANDOM.nextGaussian() * DROPLET_SPREAD;
            double z = this.posZ + RANDOM.nextGaussian() * DROPLET_SPREAD;
            particles.add(LavaDroplet.create(level, x, this.posY, z,
                    RANDOM.nextGaussian() * DROPLET_SCATTER, up, RANDOM.nextGaussian() * DROPLET_SCATTER));
        }
        if (pop)
            this.createParticle(ParticleTypes.LAVA, this.posX, this.posY, this.posZ, 0D, 0D, 0D).ifPresent(particles::add);
        return particles;
    }
}