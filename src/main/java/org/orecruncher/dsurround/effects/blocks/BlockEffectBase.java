package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;
import org.orecruncher.dsurround.sound.IAudioPlayer;

import java.util.Optional;
import java.util.Random;

public abstract class BlockEffectBase implements IBlockEffect {

    protected static final Random RANDOM = Randomizer.current();
    protected static final IAudioPlayer AUDIO_PLAYER = ContainerManager.resolve(IAudioPlayer.class);

    protected final Level world;
    protected final double posX;
    protected final double posY;
    protected final double posZ;
    protected final BlockPos position;
    private boolean isAlive = true;

    protected BlockEffectBase(final Level worldIn, final double posXIn, final double posYIn, final double posZIn) {
        this.world = worldIn;
        this.posX = posXIn;
        this.posY = posYIn;
        this.posZ = posZIn;
        this.position = BlockPos.containing(posXIn, posYIn, posZIn);
    }

    public BlockPos getPos() {
        return this.position;
    }

    /**
     * Adds a particle to the Minecraft particle system
     */
    public void addParticle(final Particle particle) {
        GameUtils.getParticleManager().add(particle);
    }

    /**
     * Adds a particle to the Minecraft particle system
     */
    public void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        GameUtils.getParticleManager().createParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }

    /**
     * Creates a particle effect but does not queue.  Allows for the particle to be manipulated prior to handing
     * it to the particle manager.
     */
    public <T extends ParticleOptions> Optional<Particle> createParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        var pm = GameUtils.getParticleManager();
        var t = (MixinParticleManager) pm;
        return Optional.ofNullable(t.dsurround_createParticle(parameters, x, y, z, velocityX, velocityY, velocityZ));
    }

    public boolean isDone() {
        return !this.isAlive;
    }

    public void remove() {
        this.isAlive = false;
        cleanUp();
    }

    /**
     * By default, a system will stay alive indefinitely until the
     * ParticleSystemManager removes it. Override to provide termination capability.
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
            remove();
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