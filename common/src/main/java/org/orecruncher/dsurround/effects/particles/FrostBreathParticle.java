package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class FrostBreathParticle extends SingleQuadParticle {

    private final SpriteSet spriteProvider;

    public static Particle create(LivingEntity entity) {
        SpriteSet spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.CLOUD);
        if (spriteProvider != null) {
            return new FrostBreathParticle(entity, spriteProvider);
        }

        // Fallback to a vanilla cloud if the custom particle constructor cannot safely obtain sprites.
        final Vec3 origin = ParticleUtils.getBreathOrigin(entity);
        final Vec3 trajectory = ParticleUtils.getLookTrajectory(entity);
        return ParticleUtils.createParticle(
                ParticleTypes.CLOUD,
                origin.x,
                origin.y,
                origin.z,
                trajectory.x * 0.01D,
                trajectory.y * 0.01D,
                trajectory.z * 0.01D
        );
    }

    private FrostBreathParticle(LivingEntity entity, SpriteSet spriteProvider) {
        super((ClientLevel) entity.level(), 0, 0, 0, 0.0, 0.0, 0.0, spriteProvider.get(0, 1));

        final IRandomizer rand = Randomizer.current();

        this.spriteProvider = spriteProvider;
        final Vec3 origin = ParticleUtils.getBreathOrigin(entity);
        final Vec3 trajectory = ParticleUtils.getLookTrajectory(entity);

        this.setPos(origin.x, origin.y, origin.z);
        this.xo = origin.x;
        this.yo = origin.y;
        this.zo = origin.z;

        this.xd = trajectory.x * 0.01D;
        this.yd = trajectory.y * 0.01D;
        this.zd = trajectory.z * 0.01D;

        this.setAlpha(0.2F);
        float f1 = 1.0F - (float) (rand.nextDouble() * 0.3F);
        this.rCol = f1;
        this.gCol = f1;
        this.bCol = f1;
        this.quadSize *= 1.875F * (entity.isBaby() ? 0.125F : 0.25F);
        int i = (int) (8.0D / (rand.nextDouble() * 0.8D + 0.3D));
        this.lifetime = (int) Math.max((float) i * 2.5F, 1.0F);
        this.hasPhysics = false;
        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    protected @NotNull Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return this.quadSize * Mth.clamp(((float)this.age + tickDelta) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.spriteProvider);
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.96F;
            this.yd *= 0.96F;
            this.zd *= 0.96F;

            if (this.onGround) {
                this.xd *= 0.7F;
                this.zd *= 0.7F;
            }
        }
    }


    @Override
    public int getLightCoords(float partialTick) {
        return 15728880;
    }
}
