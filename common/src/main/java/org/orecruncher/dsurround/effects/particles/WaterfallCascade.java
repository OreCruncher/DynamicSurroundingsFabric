package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.effects.BlockEffectUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.math.MathStuff;

public class WaterfallCascade extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final int ageJitter;

    public static Particle create(ClientLevel level, double x, double y, double z, int strength) {
        // Calculate the quad size based on waterfall strength
        var quadSize = 0.5F + 4.5F * ((strength - 1) / (float)BlockEffectUtils.MAX_STRENGTH);
        var sprites = ParticleUtils.getSpriteProvider(DSurroundParticleTypes.WATERFALL_CASCADE);
        return new WaterfallCascade(level, x, y, z, sprites, quadSize);
    }

    protected WaterfallCascade(ClientLevel clientLevel, double d, double e, double f, SpriteSet spriteProvider, float quadSize) {
        super(clientLevel, d, e, f);
        this.lifetime = 10;
        this.ageJitter = clientLevel.random.nextInt(this.lifetime);
        this.sprites = spriteProvider;
        this.quadSize = quadSize;
        this.setAlpha(1F);
        this.setSpriteFromAge(this.sprites);

        // Set the color. It would be the biome water color shifted toward white.
        var position = BlockPos.containing(this.x, this.y, this.z);
        var biomeColor = this.level.getBiome(position).value().getWaterColor();
        var colorRgb = FastColor.ARGB32.lerp(0.5F, biomeColor, ColorPalette.MC_WHITE.getValue());
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;

    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return DSurroundParticleRenderType.PARTICLE_SHEET_WATERFALL_CASCADE;
    }

    @Override
    public void setSpriteFromAge(@NotNull SpriteSet spriteSet) {
        if (!this.removed) {
            // This is to provide some variation to avoid having all particles
            // look the same at a given tick.
            var fudgedAge = MathStuff.wrap(this.age + this.ageJitter, this.lifetime);
            this.setSprite(spriteSet.get(fudgedAge, this.lifetime));
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }
}
