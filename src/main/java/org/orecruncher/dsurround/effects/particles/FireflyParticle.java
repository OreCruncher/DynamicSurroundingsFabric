package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class FireflyParticle extends SimpleAnimatedParticle {
    private static final IRandomizer RANDOM = Randomizer.current();
    private static final float XZ_MOTION_DELTA = 0.1F;
    private static final float Y_MOTION_DELTA = XZ_MOTION_DELTA / 2.0F;
    private static final float ACCELERATION = 0.004F;
    private static final SpriteSet spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.END_ROD);

    private final double xAcceleration;
    private final double yAcceleration;
    private final double zAcceleration;

    public FireflyParticle(Level world, double x, double y, double z) {
        super((ClientLevel)world, x, y, z, spriteProvider, 0F);
        this.quadSize *= 0.35f;
        this.lifetime = 60 + this.random.nextInt(12);
        this.setColor(ColorPalette.MC_YELLOW.getValue());
        this.setFadeColor(ColorPalette.MC_GREEN.getValue());
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

    // From GlowParticle
    @Override
    public int getLightColor(float f) {
        float g = ((float)this.age + f) / (float)this.lifetime;
        g = Mth.clamp(g, 0.0f, 1.0f);
        int i = super.getLightColor(f);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        if ((j += (int)(g * 15.0f * 16.0f)) > 240) {
            j = 240;
        }
        return j | k << 16;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        this.xd += this.xAcceleration;
        this.yd += this.yAcceleration;
        this.zd += this.zAcceleration;

        this.setBoundingBox(this.getBoundingBox().move(this.xd, this.yd, this.zd));
        this.setLocationFromBoundingbox();
    }
}