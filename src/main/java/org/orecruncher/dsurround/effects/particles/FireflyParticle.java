package org.orecruncher.dsurround.effects.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class FireflyParticle extends AnimatedParticle {
    private static final Random RANDOM = XorShiftRandom.current();
    private static final float XZ_MOTION_DELTA = 0.1F;
    private static final float Y_MOTION_DELTA = XZ_MOTION_DELTA / 2.0F;
    private static final float ACCELERATION = 0.004F;
    private static final SpriteProvider spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.END_ROD);

    private final double xAcceleration;
    private final double yAcceleration;
    private final double zAcceleration;

    public FireflyParticle(World world, double x, double y, double z) {
        super((ClientWorld)world, x, y, z, spriteProvider, 0F);
        this.scale *= 0.35f;
        this.maxAge = 60 + this.random.nextInt(12);
        this.setColor(ColorPalette.MC_YELLOW.getRGB());
        this.setTargetColor(ColorPalette.MC_GREEN.getRGB());
        this.setSpriteForAge(spriteProvider);

        this.velocityX = RANDOM.nextGaussian() * XZ_MOTION_DELTA;
        this.velocityY = RANDOM.nextGaussian() * Y_MOTION_DELTA;
        this.velocityZ = RANDOM.nextGaussian() * XZ_MOTION_DELTA;
        this.velocityMultiplier = 1F;   // Effectively turns it off since we are going to manage it

        this.xAcceleration = RANDOM.nextGaussian() * ACCELERATION;
        this.yAcceleration = RANDOM.nextGaussian() / 2.0D * ACCELERATION;
        this.zAcceleration = RANDOM.nextGaussian() * ACCELERATION;

        this.gravityStrength = 0F;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        this.velocityX += this.xAcceleration;
        this.velocityY += this.yAcceleration;
        this.velocityZ += this.zAcceleration;

        this.setBoundingBox(this.getBoundingBox().offset(this.velocityX, this.velocityY, this.velocityZ));
        this.repositionFromBoundingBox();
    }
}