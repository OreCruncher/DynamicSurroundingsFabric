package org.orecruncher.dsurround.effects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public abstract class ParticleJetEffect extends BlockEffectBase {

    protected final int jetStrength;
    protected final int updateFrequency;

    protected final int particleMaxAge;
    protected int particleAge;

    public ParticleJetEffect(final int strength, final World world, final double x, final double y, final double z) {
        this(0, strength, world, x, y, z, 3);
    }

    public ParticleJetEffect(final int layer, final int strength, final World world, final double x, final double y,
               final double z, final int freq) {
        super(world, x, y, z);

        this.jetStrength = strength;
        this.updateFrequency = freq;
        this.particleMaxAge = (RANDOM.nextInt(strength) + 2) * 20;
    }

    /*
     * Override in derived class to provide particle for the jet.
     */
    protected abstract void spawnJetParticle();

    @Override
    public boolean shouldDie() {
        return this.particleAge >= this.particleMaxAge;
    }

    /*
     * During update see if a particle needs to be spawned so that it can rise up.
     */
    @Override
    public void think() {

        // Check to see if a particle needs to be generated
        if (this.particleAge % this.updateFrequency == 0) {
            spawnJetParticle();
        }

        // Grow older
        this.particleAge++;
    }
}