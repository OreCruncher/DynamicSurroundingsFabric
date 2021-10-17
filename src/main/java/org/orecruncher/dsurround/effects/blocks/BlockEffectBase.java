package org.orecruncher.dsurround.effects.blocks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;

import java.util.Random;

@Environment(EnvType.CLIENT)
public abstract class BlockEffectBase implements IBlockEffect {

    protected static final Random RANDOM = XorShiftRandom.current();

    protected final World world;
    protected final double posX;
    protected final double posY;
    protected final double posZ;
    protected final BlockPos position;
    private boolean isAlive = true;

    protected BlockEffectBase(final World worldIn, final double posXIn, final double posYIn, final double posZIn) {
        this.world = worldIn;
        this.posX = posXIn;
        this.posY = posYIn;
        this.posZ = posZIn;
        this.position = new BlockPos(posXIn, posYIn, posZIn);
    }

    public BlockPos getPos() {
        return this.position;
    }

    /**
     * Adds a particle to the Minecraft particle system
     */
    public void addParticle(final Particle particle) {
        GameUtils.getMC().particleManager.addParticle(particle);
    }

    /**
     * Adds a particle to the Minecraft particle system
     */
    public void addParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        GameUtils.getMC().particleManager.addParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }

    /**
     * Creates a particle effect but does not queue.  Allows for the particle to be manipulated prior to handing
     * it to the particle manager.
     */
    public <T extends ParticleEffect> Particle createParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        return ((MixinParticleManager) GameUtils.getMC().particleManager)
                .dsurroundCreateParticle(
                        parameters,
                        x, y, z,
                        velocityX, velocityY, velocityZ);
    }

    public boolean isDone() {
        return !this.isAlive;
    }

    public void setDone() {
        this.isAlive = false;
        cleanUp();
    }

    /**
     * By default, a system will stay alive indefinitely until the
     * ParticleSystemManager kills it. Override to provide termination capability.
     */
    public boolean shouldDie() {
        return false;
    }

    /**
     * Perform any cleanup activities prior to dying.
     */
    protected void cleanUp() {

    }

    /**
     * Update the state of the particle system. Any particles are queued into the
     * Minecraft particle system or to a ParticleCollection so they do not have to
     * be ticked.
     */
    public void tick() {
        if (shouldDie()) {
            setDone();
            return;
        }

        // Let the system mull over what it wants to do
        think();

        if (!isDone())
            // Update any sounds
            soundUpdate();
    }

    /**
     * Override to provide sound for the particle effect. Will be invoked whenever
     * the particle system is updated by the particle manager.
     */
    protected void soundUpdate() {

    }

    /**
     * Override to provide some sort of intelligence to the system. The logic can do
     * things like add new particles, remove old ones, update positions, etc. Will
     * be invoked during the systems onUpdate() call.
     */
    public abstract void think();

}