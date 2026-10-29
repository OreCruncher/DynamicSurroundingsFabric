package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.SceneWind;
import org.orecruncher.dsurround.runtime.weather.Shelter;

public class FrostBreathParticle extends SingleQuadParticle {

    // The share of its speed it keeps each tick
    private static final float DRAG = 0.96F;

    // How much of the wind reaches it, 0 to 1; below 0 until found, on its first tick
    private float windExposure = -1F;

    private final SpriteSet spriteProvider;

    /**
     * A puff breathed out at the origin, along the trajectory.
     *
     * @param size  the puff's size: a quarter of a cloud for a grown player
     * @param speed how fast it leaves the mouth, in blocks a tick; drag stops it about 25 times that far out
     */
    public static Particle create(LivingEntity entity, Vec3 origin, Vec3 trajectory, float size, double speed) {
        SpriteSet spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.CLOUD);
        if (spriteProvider != null) {
            return new FrostBreathParticle(entity, origin, trajectory, size, speed, spriteProvider);
        }

        // Fallback to a vanilla cloud if the custom particle constructor cannot safely obtain sprites.
        return ParticleUtils.createParticle(
                ParticleTypes.CLOUD,
                origin.x,
                origin.y,
                origin.z,
                trajectory.x * speed,
                trajectory.y * speed,
                trajectory.z * speed
        );
    }

    private FrostBreathParticle(LivingEntity entity, Vec3 origin, Vec3 trajectory, float size, double speed, SpriteSet spriteProvider) {
        super((ClientLevel) entity.level(), 0, 0, 0, 0.0, 0.0, 0.0, spriteProvider.first());
        
        final IRandomizer rand = Randomizer.current();

        // Reuse the cloud sheet
        this.spriteProvider = spriteProvider;

        this.setPos(origin.x, origin.y, origin.z);
        this.xo = origin.x;
        this.yo = origin.y;
        this.zo = origin.z;

        this.xd = trajectory.x * speed;
        this.yd = trajectory.y * speed;
        this.zd = trajectory.z * speed;

        this.setAlpha(0.2F);
        float f1 = 1.0F - (float) (rand.nextDouble() * (double) 0.3F);
        this.rCol = f1;
        this.gCol = f1;
        this.bCol = f1;
        this.quadSize *= 1.875F * size;
        int i = (int) (8.0D / (rand.nextDouble() * 0.8D + 0.3D));
        this.lifetime = (int) Math.max((float) i * 2.5F, 1.0F);
        this.hasPhysics = false;
        this.setSpriteFromAge(this.spriteProvider);
    }

    /**
     * Soft, as the mist and steam are (see SoftParticles): fading where it meets a wall, and writing no depth of its
     * own. A breath is a large faint puff right in front of the face, and with its depth written, whatever is drawn after
     * the particles and tested against depth took it for something close by and left out what lay behind it: the frost
     * on a window, or on 1.21.1, with Astral Sorcery's pitch black night sky, the dark itself.
     */
    @Override
    public @NotNull Layer getLayer() {
        return SoftParticles.mistLayer();
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return this.quadSize * Mth.clamp(((float)this.age + tickDelta) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.spriteProvider);
            // Drifts downwind, as far as it is out in the open; found where it is first breathed out
            if (this.windExposure < 0F)
                this.windExposure = Shelter.exposure(this.level, BlockPos.containing(this.x, this.y, this.z));
            this.xd += SceneWind.push(SceneWind.x(this.x, this.z), this.windExposure, DRAG);
            this.zd += SceneWind.push(SceneWind.z(this.x, this.z), this.windExposure, DRAG);
            this.move(this.xd, this.yd, this.zd);
            this.xd *= DRAG;
            this.yd *= DRAG;
            this.zd *= DRAG;

            if (this.onGround) {
                this.xd *= 0.7F;
                this.zd *= 0.7F;
            }
        }
    }
}
