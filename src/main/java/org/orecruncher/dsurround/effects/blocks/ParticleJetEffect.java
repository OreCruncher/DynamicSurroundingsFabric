package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.world.World;

public abstract class ParticleJetEffect extends BlockEffectBase {

    protected final int jetStrength;
    protected final int updateFrequency;

    protected final int particleMaxAge;
    protected int particleAge;

    public ParticleJetEffect(World world, double x, double y, double z) {
        this(1, world, x, y, z);
    }

    public ParticleJetEffect(int strength, World world, double x, double y, double z) {
        this(strength, world, x, y, z, 3);
    }

    public ParticleJetEffect(int strength, World world, double x, double y, double z, int freq) {
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
     * Ages the effect and detects whether a particle needs to be emitted.
     */
    @Override
    public void think() {

        // Check to see if a particle needs to be generated
        if (this.particleAge % this.updateFrequency == 0) {
            this.spawnJetParticle();
        }

        // Grow older
        this.particleAge++;
    }
}