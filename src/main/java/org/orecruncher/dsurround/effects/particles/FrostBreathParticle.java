package org.orecruncher.dsurround.effects.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class FrostBreathParticle extends SpriteBillboardParticle {

    private final SpriteProvider spriteProvider;

    public FrostBreathParticle(LivingEntity entity) {
        super((ClientWorld)entity.getEntityWorld(), 0, 0, 0, 0.0, 0.0, 0.0);
        
        final Random rand = XorShiftRandom.current();

        // Reuse the cloud sheet
        this.spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.CLOUD);
        final Vec3d origin = ParticleUtils.getBreathOrigin(entity);
        final Vec3d trajectory = ParticleUtils.getLookTrajectory(entity);

        this.setPos(origin.x, origin.y, origin.z);
        this.prevPosX = origin.x;
        this.prevPosY = origin.y;
        this.prevPosZ = origin.z;

        this.velocityX = trajectory.x * 0.01D;
        this.velocityY = trajectory.y * 0.01D;
        this.velocityZ = trajectory.z * 0.01D;

        this.setColorAlpha(0.2F);
        float f1 = 1.0F - (float) (rand.nextDouble() * (double) 0.3F);
        this.colorRed = f1;
        this.colorGreen = f1;
        this.colorBlue = f1;
        this.scale *= 1.875F * (entity.isBaby() ? 0.125F : 0.25F);
        int i = (int) (8.0D / (rand.nextDouble() * 0.8D + 0.3D));
        this.maxAge = (int) Math.max((float) i * 2.5F, 1.0F);
        this.collidesWithWorld = false;
        this.setSpriteForAge(this.spriteProvider);
    }

    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public float getSize(float tickDelta) {
        return this.scale * MathHelper.clamp(((float)this.age + tickDelta) / (float)this.maxAge * 32.0F, 0.0F, 1.0F);
    }

    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge) {
            this.markDead();
        } else {
            this.setSpriteForAge(this.spriteProvider);
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= 0.96F;
            this.velocityY *= 0.96F;
            this.velocityZ *= 0.96F;

            if (this.onGround) {
                this.velocityX *= 0.7F;
                this.velocityZ *= 0.7F;
            }
        }
    }
}
