package org.orecruncher.dsurround.effects.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.*;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.lib.GameUtils;

import java.awt.*;

@Environment(EnvType.CLIENT)
public class WaterRippleParticle extends SpriteBillboardParticle {

    private static final float TEX_SIZE_HALF = 0.5F;
    private static final int BLOCKS_FROM_FADE = 5;
    private static final int MAX_BLOCKS_FADE = 12;

    private final WaterRippleStyle rippleStyle;

    private final float growthRate;
    private final float scaledWidth;
    private float texU1;
    private float texU2;
    private float texV1;
    private float texV2;
    private final float defaultColorAlpha;

    public WaterRippleParticle(WaterRippleStyle rippleStyle, ClientWorld world, double x, double y, double z) {
        super(world, x, y, z, 0.0, 0.0, 0.0);

        this.rippleStyle = rippleStyle;
        this.maxAge = rippleStyle.getMaxAge();

        if (rippleStyle.doScaling()) {
            this.growthRate = this.maxAge / 500F;
            this.scale = this.growthRate;
            this.scaledWidth = this.scale * TEX_SIZE_HALF;
        } else {
            this.growthRate = 0F;
            this.scale = 1F;
            this.scaledWidth = 0.5F;
        }

        this.y -= 0.2D;

        assert GameUtils.getPlayer() != null;
        var cameraPos = GameUtils.getPlayer().getCameraBlockPos();
        var position = new BlockPos(this.x, this.y, this.z);

        var color = new Color(this.world.getBiome(position).getWaterColor());
        this.colorRed = color.getRed() / 255F;
        this.colorGreen = color.getGreen() / 255F;
        this.colorBlue = color.getBlue() / 255F;

        float distance = (float) MathHelper.clamp(
                Math.sqrt(cameraPos.getSquaredDistance(position)) - BLOCKS_FROM_FADE,
                0,
                MAX_BLOCKS_FADE
        );
        this.colorAlpha = this.defaultColorAlpha = 0.60F * (MAX_BLOCKS_FADE - distance) / MAX_BLOCKS_FADE;

        this.texU1 = rippleStyle.getU1(this.age);
        this.texU2 = rippleStyle.getU2(this.age);
        this.texV1 = rippleStyle.getV1(this.age);
        this.texV2 = rippleStyle.getV2(this.age);
    }

    public ParticleTextureSheet getType() {
        return ParticleSheets.RIPPLE_RENDER;
    }

    public float getSize(float tickDelta) {
        return this.scale * MathHelper.clamp(((float)this.age + tickDelta) / (float)this.maxAge * 32.0F, 0.0F, 1.0F);
    }

    protected float getMinU() {
        return this.texU1;
    }

    protected float getMaxU() {
        return this.texU2;
    }

    protected float getMinV() {
        return this.texV1;
    }

    protected float getMaxV() {
        return this.texV2;
    }

    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Vec3d vec3d = camera.getPos();
        float X = (float)(MathHelper.lerp(tickDelta, this.prevPosX, this.x) - vec3d.getX());
        float Y = (float)(MathHelper.lerp(tickDelta, this.prevPosY, this.y) - vec3d.getY());
        float Z = (float)(MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - vec3d.getZ());

        int p = this.getBrightness(tickDelta);

        vertexConsumer
                .vertex(-this.scaledWidth + X, Y, this.scaledWidth + Z)
                .texture(this.texU2, this.texV2).color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .light(p)
                .next();
        vertexConsumer
                .vertex(this.scaledWidth + X, Y, this.scaledWidth + Z)
                .texture( this.texU2, this.texV1).color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .light(p)
                .next();
        vertexConsumer
                .vertex(this.scaledWidth + X, Y, -this.scaledWidth + Z)
                .texture( this.texU1, this.texV1)
                .color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .light(p)
                .next();
        vertexConsumer
                .vertex(-this.scaledWidth + X, Y, -this.scaledWidth + Z)
                .texture(this.texU1, this.texV2)
                .color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .light(p)
                .next();
    }

    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;

        if (this.age++ >= this.maxAge) {
            this.markDead();
        } else {
            if (this.rippleStyle.doScaling()) {
                this.scale += this.growthRate;
            }

            if (this.rippleStyle.doAlpha()) {
                this.colorAlpha = this.defaultColorAlpha * (float) (this.maxAge - this.age)/this.maxAge;
            }

            this.texU1 = this.rippleStyle.getU1(this.age);
            this.texU2 = this.rippleStyle.getU2(this.age);
            this.texV1 = this.rippleStyle.getV1(this.age);
            this.texV2 = this.rippleStyle.getV2(this.age);
        }
    }
}
