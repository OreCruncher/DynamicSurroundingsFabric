package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.orecruncher.dsurround.config.WaterRippleStyle;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * Dynamic Surroundings water ripple rendered with 26.x atlas-backed particle sprites.
 */
public class WaterRippleParticle extends SingleQuadParticle {

    private static final int BLOCKS_FROM_FADE = 5;
    private static final int MAX_BLOCKS_FADE = 12;

    private final WaterRippleStyle rippleStyle;
    private final SpriteSet spriteProvider;
    private final float growthRate;
    private final float defaultColorAlpha;

    public static Particle create(WaterRippleStyle rippleStyle, ClientLevel world, double x, double y, double z) {
        SpriteSet spriteProvider = spriteProviderFor(rippleStyle);
        if (spriteProvider != null) {
            return new WaterRippleParticle(rippleStyle, world, x, y, z, spriteProvider);
        }

        // Last-resort fallback.  This should rarely be used because DS ripple SpriteSets are captured
        // during Fabric particle provider registration, but it avoids crashing worlds if resource reload
        // order changes.
        return ParticleUtils.createParticle(ParticleTypes.SPLASH, x, y, z, 0D, 0D, 0D);
    }

    private static SpriteSet spriteProviderFor(WaterRippleStyle rippleStyle) {
        var sprites = ParticleUtils.getSpriteProvider(DsurroundParticleTypes.forRippleStyle(rippleStyle));
        if (sprites == null) {
            sprites = ParticleUtils.getSpriteProvider(ParticleTypes.FISHING);
        }
        if (sprites == null) {
            sprites = ParticleUtils.getSpriteProvider(ParticleTypes.SPLASH);
        }
        return sprites;
    }

    private WaterRippleParticle(WaterRippleStyle rippleStyle, ClientLevel world, double x, double y, double z, SpriteSet spriteProvider) {
        super(world, x, y, z, 0.0, 0.0, 0.0, spriteProvider.first());

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
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.gravity = 0.0F;
        this.hasPhysics = false;

        var player = GameUtils.getPlayer().orElse(null);
        var cameraPos = player != null ? BlockPos.containing(player.getEyePosition(1.0f)) : BlockPos.containing(x, y, z);
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
        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    protected @NotNull Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        var rotation = new Quaternionf().lookAlong(new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)).normalize();
        this.extractRotatedQuad(state, camera, rotation, partialTick);
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return this.quadSize * Mth.clamp(((float)this.age + tickDelta) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public int getLightCoords(float partialTick) {
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

        if (this.rippleStyle.doAlpha()) {
            this.alpha = this.defaultColorAlpha * (float) (this.lifetime - this.age) / this.lifetime;
        }

        this.setSpriteFromAge(this.spriteProvider);
    }
}
