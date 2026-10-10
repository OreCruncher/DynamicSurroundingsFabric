package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class FireflyParticle extends SimpleAnimatedParticle {
    private static final IRandomizer RANDOM = Randomizer.current();
    private static final float XZ_MOTION_DELTA = 0.03F; //0.04F;
    private static final float Y_MOTION_DELTA = XZ_MOTION_DELTA / 2.0F;

    // A slow, meandering flight, climbing by up to its lift in the "J" stroke (see stroke)
    private static final WanderingFlight.Settings FLIGHT = new WanderingFlight.Settings(
            0.012F,  // minSpeed
            0.03F,   // maxSpeed
            0.025F,  // turnWander
            0.9F,    // turnDamping
            0.12F,   // steer
            0.018F,  // lift
            0.004F,  // bob
            0.2F,    // bobRate
            6);      // lookAhead

    // The halo: a soft glow around the firefly, this many times its size, at this strength
    private static final float HALO_SCALE = 5F;
    private static final float HALO_ALPHA = 0.35F;

    // A firefly's light: yellow-green, about 560nm
    private static final float LIGHT_RED = 0.72F;
    private static final float LIGHT_GREEN = 1F;
    private static final float LIGHT_BLUE = 0.22F;
    // Dimmer than this, nothing is drawn
    private static final float MIN_VISIBLE = 0.004F;

    private final FireflyFlash flash;
    private final WanderingFlight flight;
    private final WanderingFlight.Obstacles obstacles;
    private final TextureAtlasSprite haloSprite;

    public static Particle create(Level level, double x, double y, double z) {

        SpriteSet spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.END_ROD);
        if (spriteProvider != null) {
            return new FireflyParticle(level, x, y, z, spriteProvider);
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

    private FireflyParticle(Level level, double x, double y, double z, SpriteSet spriteProvider) {
        super((ClientLevel)level, x, y, z, spriteProvider, 0F);
        this.quadSize *= 0.20f + (float)(this.random.nextGaussian() * 0.1f);
        // Lives for a few flashes; dark in between
        this.flash = new FireflyFlash(this.random);
        this.lifetime = this.flash.lifetime();
        // The yellow-green of a firefly's light
        this.setColor(LIGHT_RED, LIGHT_GREEN, LIGHT_BLUE);
        this.setSpriteFromAge(spriteProvider);

        // Already under way when it appears
        this.flight = new WanderingFlight(FLIGHT, this.random);
        this.obstacles = WanderingFlight.blocksIn(level);
        this.xd = this.flight.xd();
        this.yd = this.flight.yd();
        this.zd = this.flight.zd();
        this.friction = 1F;   // Effectively turns it off since the flight manages it

        this.gravity = 0F;

        this.haloSprite = DSurroundParticleSprites.FIREFLY_GLOW.get(0, 1);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return DSurroundParticleRenderType.PARTICLE_SHEET_FIREFLY;
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera camera, float partialTicks) {
        // Dark between flashes, like a real firefly
        var glow = this.flash.brightness(this.age + partialTicks);
        if (glow < MIN_VISIBLE)
            return;

        var options = Client.Config.fireflyOptions;
        if (options.enableGlow) {
            // Drawn as the firefly is, with the halo's sprite, size and strength swapped in for it
            var sprite = this.sprite;
            var size = this.quadSize;
            this.sprite = this.haloSprite;
            this.quadSize = size * HALO_SCALE;
            this.alpha = HALO_ALPHA * glow;
            super.render(buffer, camera, partialTicks);
            this.sprite = sprite;
            this.quadSize = size;
        }

        this.alpha = glow;
        super.render(buffer, camera, partialTicks);

        if (FireflyLights.isActive()) {
            FireflyLights.add(
                    Mth.lerp(partialTicks, this.xo, this.x),
                    Mth.lerp(partialTicks, this.yo, this.y),
                    Mth.lerp(partialTicks, this.zo, this.z),
                    this.rCol, this.gCol, this.bCol,
                    FireflyLights.STRENGTH * glow);
        }
    }

    // Lit by its own light while it flashes, however dark it is around it (as GlowParticle)
    @Override
    public int getLightColor(float f) {
        int i = super.getLightColor(f);
        int block = i & 0xFF;
        int sky = i >> 16 & 0xFF;
        block = Math.max(block, (int) (this.flash.brightness(this.age + f) * 240F));
        return block | sky << 16;
    }

    @Override
    public void tick() {
        // The "J" stroke: dipping just before each flash, rising while lit
        this.flight.tick(this.x, this.y, this.z, this.age, this.flash.climb(this.age), this.obstacles);
        this.xd = this.flight.xd();
        this.yd = this.flight.yd();
        this.zd = this.flight.zd();
        super.tick();
    }

    @Override
    public void move(double dx, double dy, double dz) {
        // No collisions: the firefly steers around blocks instead (see WanderingFlight). Vanilla's collision would stop it
        // dead for good the first time it touched a floor or ceiling.
        this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
        this.setLocationFromBoundingbox();
    }
}