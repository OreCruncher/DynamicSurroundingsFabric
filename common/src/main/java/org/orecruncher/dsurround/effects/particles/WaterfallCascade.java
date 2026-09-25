package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.orecruncher.dsurround.effects.BlockEffectUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.math.MathStuff;

public class WaterfallCascade extends SingleQuadParticle {

    private final static Vector3f DEFAULT_NORMAL = new Vector3f(0, 0, 1);

    private final Vec3 position;
    private final SpriteSet sprites;
    private final int ageJitter;

    public static Particle create(ClientLevel level, double x, double y, double z, int strength) {
        // Calculate the quad size based on waterfall strength
        var quadSize = 0.5F + 4.5F * ((strength - 1) / (float)BlockEffectUtils.MAX_STRENGTH);
        var sprites = ParticleUtils.getSpriteProvider(DSurroundParticleTypes.WATERFALL_CASCADE);
        return new WaterfallCascade(level, x, y, z, sprites, quadSize);
    }

    protected WaterfallCascade(ClientLevel clientLevel, double x, double y, double z, SpriteSet spriteProvider, float quadSize) {
        super(clientLevel, x, y, z, spriteProvider.first());
        this.position = new Vec3(x, y, z);
        this.lifetime = 15;
        this.ageJitter = clientLevel.getRandom().nextInt(this.lifetime);
        this.sprites = spriteProvider;
        this.quadSize = quadSize;
        this.setAlpha(1F);
        this.setSpriteFromAge(this.sprites);

        // Set the color. It would be the biome water color shifted toward white.
        var position = BlockPos.containing(this.x, this.y, this.z);
        var biomeColor = this.level.getBiome(position).value().getWaterColor();
        // TODO: Figure out which lerp to use
        var colorRgb = ARGB.linearLerp(0.5F, biomeColor, ColorPalette.MC_WHITE.getValue());
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;
    }

    @Override
    public @NotNull Layer getLayer() {
        return DSurroundParticleRenderType.PARTICLE_SHEET_WATERFALL_CASCADE;
    }

    @Override
    public void extract(final @NonNull QuadParticleRenderState particleTypeRenderState, Camera camera, float tickDelta) {
        // Calculate the position dynamically based on the true particle
        // position and the camera. The particle should be rendered as a billboard
        // and constantly shifting in front of the source.
        var normal = camera
                .position()
                .subtract(this.position)
                .normalize();

        var renderPosition = normal
                .scale(1.5D)
                .add(this.position);

        this.x = renderPosition.x;
        this.y = renderPosition.y;
        this.z = renderPosition.z;

        // Do this so that the particle is facing the player position and not somehow
        // related to the camera. (Doing it with the camera makes the particle rotate weirdly
        // when the player view shifts away from the particle location.) Thanks, Gemini!
        var targetRotation = new Quaternionf().rotateTo(DEFAULT_NORMAL, normal.toVector3f());
        this.extractRotatedQuad(particleTypeRenderState, camera, targetRotation, tickDelta);
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
