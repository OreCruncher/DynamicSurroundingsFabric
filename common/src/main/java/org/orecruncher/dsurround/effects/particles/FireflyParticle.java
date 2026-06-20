package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class FireflyParticle extends SimpleAnimatedParticle {
    private static final IRandomizer RANDOM = Randomizer.current();
    private static final float XZ_MOTION_DELTA = 0.03F; //0.04F;
    private static final float Y_MOTION_DELTA = XZ_MOTION_DELTA / 2.0F;
    private static final float ACCELERATION = 0.0005F; //0.0015F;

    private final double xAcceleration;
    private final double yAcceleration;
    private final double zAcceleration;

    public static Particle create(Level world, double x, double y, double z) {
        SpriteSet spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.END_ROD);
        if (spriteProvider != null) {
            return new FireflyParticle(world, x, y, z, spriteProvider);
        }

        // Fallback: keep the effect alive even if the vanilla SpriteSet cache cannot be read.
        return ParticleUtils.createParticle(
                ParticleTypes.END_ROD,
                x,
                y,
                z,
                RANDOM.nextGaussian() * XZ_MOTION_DELTA,
                RANDOM.nextGaussian() * Y_MOTION_DELTA,
                RANDOM.nextGaussian() * XZ_MOTION_DELTA
        );
    }

    private FireflyParticle(Level world, double x, double y, double z, SpriteSet spriteProvider) {
        super((ClientLevel)world, x, y, z, spriteProvider, 0F);
        this.quadSize *= 0.20f + (float)(this.random.nextGaussian() * 0.1f);
        this.lifetime = 60 + this.random.nextInt(12);
        this.setColor(ColorPalette.ELECTRIC_GREEN.getValue());
        this.setFadeColor(ColorPalette.LEMON.getValue());
        this.setSpriteFromAge(spriteProvider);

        this.xd = RANDOM.nextGaussian() * XZ_MOTION_DELTA;
        this.yd = RANDOM.nextGaussian() * Y_MOTION_DELTA;
        this.zd= RANDOM.nextGaussian() * XZ_MOTION_DELTA;
        this.friction = 1F;   // Effectively turns it off since we are going to manage it

        this.xAcceleration = RANDOM.nextGaussian() * ACCELERATION;
        this.yAcceleration = RANDOM.nextGaussian() / 2.0D * ACCELERATION;
        this.zAcceleration = RANDOM.nextGaussian() * ACCELERATION;

        this.gravity = 0F;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        this.xd += this.xAcceleration;
        this.yd += this.yAcceleration;
        this.zd += this.zAcceleration;

        this.setBoundingBox(this.getBoundingBox().move(this.xd, this.yd, this.zd));
        this.setLocationFromBoundingbox();
    }

    @Override
    public int getLightCoords(float partialTick) {
        return 15728880;
    }
}
