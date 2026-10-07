package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

public class WaterRippleParticle extends TextureSheetParticle {

    // Lays the quad flat on the water. renderRotatedQuad() only reads it, so one instance serves every ripple.
    private static final Quaternionf FLAT = new Quaternionf().rotateX((float) Math.toRadians(-90f));

    private final WaterRippleStyle rippleStyle;
    private final SpriteSet spriteProvider;
    private final LifetimeAlpha lifetimeAlpha;

    private final float growthRate;

    public static Particle create(WaterRippleStyle rippleStyle, ClientLevel world, double x, double y, double z) {
        return new WaterRippleParticle(rippleStyle, world, x, y, z, DSurroundParticleSprites.forRippleStyle(rippleStyle));
    }

    protected WaterRippleParticle(WaterRippleStyle rippleStyle, ClientLevel world, double x, double y, double z, SpriteSet spriteProvider) {
        super(world, x, y, z, 0.0, 0.0, 0.0);

        this.rippleStyle = rippleStyle;
        this.spriteProvider = spriteProvider;
        this.lifetime = rippleStyle.getMaxAge();

        if (rippleStyle.doScaling()) {
            this.growthRate = this.lifetime / 500F;
            this.quadSize = this.growthRate;
        } else {
            this.growthRate = 0F;
            this.quadSize = 1F;
        }

        this.y -= 0.2D;

        var position = BlockPos.containing(this.x, this.y, this.z);
        var colorRgb = this.level.getBiome(position).value().getWaterColor();
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;

        if (this.rippleStyle.doAlpha()) {
            this.lifetimeAlpha = new LifetimeAlpha(0.9F, 0.0F, 0.0F, 1F);
        } else {
            this.lifetimeAlpha = LifetimeAlpha.ALWAYS_OPAQUE;
        }

        this.alpha = this.lifetimeAlpha.startAlpha();
        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(@NotNull VertexConsumer vertexConsumer, @NotNull Camera camera, float tickDelta) {
        this.setAlpha(this.lifetimeAlpha.currentAlphaForAge(this.age, this.lifetime, tickDelta));
        this.renderRotatedQuad(vertexConsumer, camera, FLAT, tickDelta);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (this.rippleStyle.doScaling()) {
            this.quadSize += this.growthRate;
        }

        this.setSpriteFromAge(this.spriteProvider);
    }
}
