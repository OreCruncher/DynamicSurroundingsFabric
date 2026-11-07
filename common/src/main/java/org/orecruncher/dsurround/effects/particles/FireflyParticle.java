// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public class FireflyParticle extends SimpleAnimatedParticle {
    private static final IRandomizer RANDOM = Randomizer.current();

    // Fireflies, with their halos: vanilla's translucent particles with depth writes off, so halos don't hide each
    // other. The lights they cast are drawn just before them (see PointLights).
    private static final RenderPipeline PIPELINE = SoftParticles.particlePipeline("pipeline/firefly", null).build();
    private static final SingleQuadParticle.Layer LAYER = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, PIPELINE, RenderPipelines.OIT_PARTICLE);

    static {
        // Drawn with a shader pack too, with the pack's program for translucent particles
        IrisCompat.assignPipeline(PIPELINE, IrisCompat.Program.PARTICLES_TRANSLUCENT);
    }
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
    // The firefly itself is the game's dot, as big as the game draws it: the game's firefly particle is three quarters
    // of a particle's usual size, and the dot is as much of that as the lit part is of the texture (see FireflyDot)
    private static final float VANILLA_SIZE = 0.75F;
    private static final float HALO_ALPHA = 0.35F;

    // A firefly's light: yellow-green, about 560nm
    private static final float LIGHT_RED = 0.72F;
    private static final float LIGHT_GREEN = 1F;
    private static final float LIGHT_BLUE = 0.22F;
    // Between flashes, the body: a muted shade of the light's color, a little see-through, brightening as it flashes
    private static final float BODY_RED = 0.48F;
    private static final float BODY_GREEN = 0.58F;
    private static final float BODY_BLUE = 0.22F;
    private static final float BODY_ALPHA = 0.8F;
    // Dimmer than this, nothing is drawn
    private static final float MIN_VISIBLE = 0.004F;

    private final FireflyFlash flash;
    private final WanderingFlight flight;
    private final WanderingFlight.Obstacles obstacles;
    private final TextureAtlasSprite haloSprite;
    // The lit part of the sprite, drawn as the firefly; and how big
    private final FireflyDot.Bounds dot;
    private final float dotSize;
    // While the firefly is being drawn: only the lit part of the sprite (see getU0)
    private boolean drawingDot;

    public static Particle create(Level level, double x, double y, double z) {

        // The game's firefly, a small bright dot; the end rod's sparkle if its sprites can't be had
        SpriteSet fireflies = ParticleUtils.getSpriteProvider(ParticleTypes.FIREFLY);
        if (fireflies != null)
            return new FireflyParticle(level, x, y, z, fireflies, FireflyDot.bounds());
        SpriteSet spriteProvider = ParticleUtils.getSpriteProvider(ParticleTypes.END_ROD);
        if (spriteProvider != null) {
            return new FireflyParticle(level, x, y, z, spriteProvider, FireflyDot.Bounds.WHOLE);
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

    private FireflyParticle(Level level, double x, double y, double z, SpriteSet spriteProvider, FireflyDot.Bounds dot) {
        super((ClientLevel)level, x, y, z, spriteProvider, 0F);
        this.dot = dot;
        this.dotSize = this.quadSize * VANILLA_SIZE * dot.size();
        // The halo's size, from which it is drawn HALO_SCALE times as big
        this.quadSize *= 0.20f + (float)(this.random.nextGaussian() * 0.1f);
        // Lives for a few flashes; only its body is seen in between
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
    public @NotNull SingleQuadParticle.Layer getLayer() {
        return LAYER;
    }

    @Override
    public void extract(@NotNull QuadParticleRenderState state, @NotNull Camera camera, float partialTicks) {
        // The body is seen between flashes, lit only by the world around it (see getLightCoords), as the game's
        // fireflies are; the halo and the light come with a flash
        var glow = this.flash.brightness(this.age + partialTicks);
        var body = Math.max(glow, BODY_ALPHA * this.flash.bodyAlpha(this.age + partialTicks));
        if (body < MIN_VISIBLE)
            return;

        var options = Client.Config.fireflyOptions;
        if (options.enableGlow && glow >= MIN_VISIBLE) {
            // Drawn as the firefly is, with the halo's sprite, size and strength swapped in for it
            var sprite = this.sprite;
            var size = this.quadSize;
            this.sprite = this.haloSprite;
            this.quadSize = size * HALO_SCALE;
            this.alpha = HALO_ALPHA * glow;
            super.extract(state, camera, partialTicks);
            this.sprite = sprite;
            this.quadSize = size;
        }

        // The firefly itself: the lit part of its sprite, at the game's size
        var size = this.quadSize;
        this.quadSize = this.dotSize;
        this.alpha = body;
        this.setColor(Mth.lerp(glow, BODY_RED, LIGHT_RED), Mth.lerp(glow, BODY_GREEN, LIGHT_GREEN), Mth.lerp(glow, BODY_BLUE, LIGHT_BLUE));
        this.drawingDot = true;
        super.extract(state, camera, partialTicks);
        this.drawingDot = false;
        this.quadSize = size;
        this.setColor(LIGHT_RED, LIGHT_GREEN, LIGHT_BLUE);

        if (glow >= MIN_VISIBLE && PointLights.fireflyLightsOn()) {
            PointLights.add(
                    Mth.lerp(partialTicks, this.xo, this.x),
                    Mth.lerp(partialTicks, this.yo, this.y),
                    Mth.lerp(partialTicks, this.zo, this.z),
                    this.rCol, this.gCol, this.bCol,
                    PointLights.FIREFLY_STRENGTH * glow, PointLights.FIREFLY_RADIUS);
        }
    }

    // While the firefly itself is drawn, only the lit part of the sprite, so the dot sits in the middle of its halo
    @Override
    protected float getU0() {
        return this.drawingDot ? this.sprite.getU(this.dot.u0()) : super.getU0();
    }

    @Override
    protected float getU1() {
        return this.drawingDot ? this.sprite.getU(this.dot.u1()) : super.getU1();
    }

    @Override
    protected float getV0() {
        return this.drawingDot ? this.sprite.getV(this.dot.v0()) : super.getV0();
    }

    @Override
    protected float getV1() {
        return this.drawingDot ? this.sprite.getV(this.dot.v1()) : super.getV1();
    }

    // Lit by its own light while it flashes, however dark it is around it (as GlowParticle)
    @Override
    public int getLightCoords(float f) {
        int i = super.getLightCoords(f);
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