package org.orecruncher.dsurround.effects.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Random;

/**
 * Just like the MC cloud particle, but with two differences:
 *
 * - The CTOR to the base class adjusts dX and dZ with gaussian factors
 * - velocityX and velocityZ are not adjusted.
 */
@Environment(EnvType.CLIENT)
public class SteamCloudParticle extends SpriteBillboardParticle {

    private static final Random RANDOM = XorShiftRandom.current();

    private final SpriteProvider spriteProvider;

    public SteamCloudParticle(ClientWorld clientWorld, double x, double y, double z, double dY, SpriteProvider spriteProvider) {
        super(clientWorld, x, y, z, RANDOM.nextGaussian() * 0.02D, dY, RANDOM.nextGaussian() * 0.02D);

        this.spriteProvider = spriteProvider;
        this.velocityX *= 0.1F;
        this.velocityY *= 0.1F;
        this.velocityZ *= 0.1F;
        this.velocityY += dY;
        float f1 = 1.0F - (float) (Math.random() * (double) 0.3F);
        this.colorRed = f1;
        this.colorGreen = f1;
        this.colorBlue = f1;
        this.scale *= 1.875F;
        int i = (int) (8.0D / (Math.random() * 0.8D + 0.3D));
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
        super.tick();
        if (!this.dead) {
            this.setSpriteForAge(this.spriteProvider);
            PlayerEntity playerEntity = this.world.getClosestPlayer(this.x, this.y, this.z, 2.0D, false);
            if (playerEntity != null) {
                double d = playerEntity.getY();
                if (this.y > d) {
                    this.y += (d - this.y) * 0.2D;
                    this.velocityY += (playerEntity.getVelocity().y - this.velocityY) * 0.2D;
                    this.setPos(this.x, this.y, this.z);
                }
            }
        }

    }

    @Environment(EnvType.CLIENT)
    public static class SteamCloudParticleFactory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider spriteProvider;

        public SteamCloudParticleFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(DefaultParticleType defaultParticleType, ClientWorld clientWorld, double x, double y, double z, double dX, double dY, double dZ) {
            return new SteamCloudParticle(clientWorld, x, y, z, dY, this.spriteProvider);
        }
    }
}