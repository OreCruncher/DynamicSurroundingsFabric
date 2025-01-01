package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

public class WaterRippleParticle extends SimpleAnimatedParticle {

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

    WaterRippleParticle(WaterRippleStyle rippleStyle, ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, 0);

        this.rippleStyle = rippleStyle;
        this.lifetime = rippleStyle.getMaxAge();

        if (rippleStyle.doScaling()) {
            this.growthRate = this.lifetime / 500F;
            this.quadSize = this.growthRate;
            this.scaledWidth = this.quadSize * TEX_SIZE_HALF;
        } else {
            this.growthRate = 0F;
            this.quadSize = 1F;
            this.scaledWidth = 0.5F;
        }

        this.y -= 0.2D;

        var player = GameUtils.getPlayer().orElseThrow();
        var cameraPos = BlockPos.containing(player.getEyePosition(1.0f));
        var position = BlockPos.containing(this.x, this.y, this.z);

        var colorRgb = this.level.getBiome(position).value().getWaterColor();
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;

        float distance = (float) Mth.clamp(
                Math.sqrt(cameraPos.distSqr(position)) - BLOCKS_FROM_FADE,
                0,
                MAX_BLOCKS_FADE
        );
        this.alpha = this.defaultColorAlpha = 0.60F * (MAX_BLOCKS_FADE - distance) / MAX_BLOCKS_FADE;

        this.texU1 = rippleStyle.getU1(this.age);
        this.texU2 = rippleStyle.getU2(this.age);
        this.texV1 = rippleStyle.getV1(this.age);
        this.texV2 = rippleStyle.getV2(this.age);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return this.quadSize * Mth.clamp(((float)this.age + tickDelta) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    protected float getU0() {
        return this.texU1;
    }

    @Override
    protected float getU1() {
        return this.texU2;
    }

    @Override
    protected float getV0() {
        return this.texV1;
    }

    @Override
    protected float getV1() {
        return this.texV2;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Vec3 vec3d = camera.getPosition();
        float X = (float)(Mth.lerp(tickDelta, this.xo, this.x) - vec3d.x());
        float Y = (float)(Mth.lerp(tickDelta, this.yo, this.y) - vec3d.y());
        float Z = (float)(Mth.lerp(tickDelta, this.zo, this.z) - vec3d.z());

        int p = this.getLightColor(tickDelta);

        vertexConsumer
                .addVertex(-this.scaledWidth + X, Y, this.scaledWidth + Z)
                .setUv(this.texU2, this.texV2)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(p);
        vertexConsumer
                .addVertex(this.scaledWidth + X, Y, this.scaledWidth + Z)
                .setUv( this.texU2, this.texV1)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(p);
        vertexConsumer
                .addVertex(this.scaledWidth + X, Y, -this.scaledWidth + Z)
                .setUv( this.texU1, this.texV1)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(p);
        vertexConsumer
                .addVertex(-this.scaledWidth + X, Y, -this.scaledWidth + Z)
                .setUv(this.texU1, this.texV2)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(p);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            if (this.rippleStyle.doScaling()) {
                this.quadSize += this.growthRate;
            }

            if (this.rippleStyle.doAlpha()) {
                this.alpha = this.defaultColorAlpha * (float) (this.lifetime - this.age)/this.lifetime;
            }

            this.texU1 = this.rippleStyle.getU1(this.age);
            this.texU2 = this.rippleStyle.getU2(this.age);
            this.texV1 = this.rippleStyle.getV1(this.age);
            this.texV2 = this.rippleStyle.getV2(this.age);

            this.setSpriteFromAge(this.sprites);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet spriteSet) {
            this.sprites = spriteSet;
        }

        @Override
        public @Nullable Particle createParticle(@NotNull SimpleParticleType particleOptions, @NotNull ClientLevel clientLevel, double x, double y, double z, double g, double h, double i) {
            return new WaterRippleParticle(WaterRippleStyle.PIXELATED_CIRCLE, clientLevel, x, y, z, this.sprites);
        }
    }
}
