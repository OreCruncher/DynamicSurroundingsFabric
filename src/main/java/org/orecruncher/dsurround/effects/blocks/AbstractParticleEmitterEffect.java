package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.client.particle.Particle;
import net.minecraft.world.level.Level;

import java.util.Optional;

public abstract class AbstractParticleEmitterEffect extends AbstractBlockEffect {

    protected final int strength;
    protected final int updateFrequency;

    protected final int ageLimit;
    protected int age;

    public AbstractParticleEmitterEffect(Level world, double x, double y, double z) {
        this(1, world, x, y, z);
    }

    public AbstractParticleEmitterEffect(int strength, Level world, double x, double y, double z) {
        this(strength, world, x, y, z, 3);
    }

    public AbstractParticleEmitterEffect(int strength, Level world, double x, double y, double z, int freq) {
        super(world, x, y, z);

        this.strength = strength;
        this.updateFrequency = freq;
        this.ageLimit = (RANDOM.nextInt(strength) + 2) * 20;
    }

    public int getStrength() {
        return this.strength;
    }

    @Override
    public boolean shouldRemove() {
        return this.age >= this.ageLimit;
    }

    /*
     * Ages the effect and detects whether a particle needs to be emitted.
     */
    @Override
    public void think() {
        // Check to see if a particle needs to be generated
        if (this.age % this.updateFrequency == 0) {
            this.handleParticles();
        }

        // Grow older
        this.age++;
    }

    protected void handleParticles() {
        this.produceParticle().ifPresent(this::addParticle);
    }

    /*
     * Override in derived class to provide particle for the jet.
     */
    protected abstract Optional<Particle> produceParticle();
}