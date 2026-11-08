// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * A wisp's light: a small pale core in a soft halo, glowing in the dark and casting a faint light on what is under it
 * (with the fireflies' lights, and their option). A light lives as long as its wisp, which moves it each tick by
 * handing it where to be ({@link #moveTo}); left without a place for a second (its wisp gone, or out of reach), it goes
 * out. A mote is a speck it sheds, drifting up and fading in a second or two.
 * <p>
 * The wisp hands it a new {@link Target} each tick, whole, so a light ticked on another thread (as some mods tick
 * particles) never reads a place half written.
 */
public class WispGlow extends SingleQuadParticle {

    /**
     * Where a light is to be, and how bright, 0 to 1
     */
    public record Target(double x, double y, double z, float brightness) {
    }

    // The halo, and the core inside it, in blocks across (twice quadSize), and how thick each is at full brightness
    private static final float HALO_SIZE = 0.6F;
    private static final float HALO_ALPHA = 0.45F;
    private static final float CORE_SIZE = 0.09F;
    private static final float CORE_ALPHA = 0.95F;
    // The core is nearly white, its color only a tint
    private static final float CORE_WHITE = 0.6F;
    // A mote, against the halo
    private static final float MOTE_SIZE = 0.12F;
    private static final float MOTE_ALPHA = 0.5F;
    // The light it casts at full brightness, 0 to 1, and how far, in blocks
    private static final float LIGHT_STRENGTH = 0.3F;
    private static final float LIGHT_RADIUS = 4F;
    // How quickly its brightness follows its wisp's, a share a tick, and how long it waits for a place before going out
    private static final float FOLLOW = 0.3F;
    private static final int STALE_TICKS = 20;
    // Dimmer than this, nothing is drawn
    private static final float MIN_VISIBLE = 0.004F;
    // Full brightness, block and sky: it lights itself
    private static final int FULL_BRIGHT = 0xF000F0;

    private final boolean mote;
    private final float red;
    private final float green;
    private final float blue;
    private volatile Target target;
    private Target followed;
    private int stale;
    private float brightness;
    private float lastBrightness;

    /**
     * A wisp's light, starting out dark where it is to be
     */
    public static WispGlow light(ClientLevel level, Target target, float red, float green, float blue) {
        return new WispGlow(level, target, red, green, blue, false);
    }

    /**
     * A speck shed by a wisp's light
     */
    public static WispGlow mote(ClientLevel level, double x, double y, double z, float brightness, float red, float green, float blue) {
        return new WispGlow(level, new Target(x, y, z, brightness), red, green, blue, true);
    }

    private WispGlow(ClientLevel level, Target target, float red, float green, float blue, boolean mote) {
        super(level, target.x(), target.y(), target.z(), DSurroundParticleSprites.FIREFLY_GLOW.get(0, 1));
        this.mote = mote;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.target = target;
        this.followed = target;
        this.hasPhysics = false;
        this.gravity = 0F;
        this.friction = 1F;
        if (mote) {
            var random = level.getRandom();
            this.lifetime = 20 + random.nextInt(20);
            this.brightness = this.lastBrightness = target.brightness();
            this.xd = (random.nextFloat() - 0.5F) * 0.01F;
            this.yd = 0.01D + random.nextFloat() * 0.01D;
            this.zd = (random.nextFloat() - 0.5F) * 0.01F;
        } else {
            // Ended by its wisp, not by age
            this.lifetime = Integer.MAX_VALUE;
        }
    }

    /**
     * Where it is to be by the end of this tick, and how bright
     */
    public void moveTo(Target target) {
        this.target = target;
    }

    @Override
    public void tick() {
        this.lastBrightness = this.brightness;
        if (this.mote) {
            this.brightness = this.followed.brightness() * (1F - (float) this.age / this.lifetime);
            super.tick();
            return;
        }
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        var target = this.target;
        if (target != this.followed) {
            this.followed = target;
            this.stale = 0;
        } else if (++this.stale > STALE_TICKS) {
            // Its wisp is gone: it goes out, then is gone too
            target = new Target(target.x(), target.y(), target.z(), 0F);
            if (this.brightness < MIN_VISIBLE) {
                this.remove();
                return;
            }
        }
        this.brightness += (target.brightness() - this.brightness) * FOLLOW;
        this.setPos(target.x(), target.y(), target.z());
        this.age++;
    }

    @Override
    public void extract(@NotNull QuadParticleRenderState state, @NotNull Camera camera, float partialTicks) {
        var shown = Mth.lerp(partialTicks, this.lastBrightness, this.brightness);
        if (shown < MIN_VISIBLE)
            return;
        if (this.mote) {
            this.draw(state, camera, partialTicks, MOTE_SIZE, MOTE_ALPHA * shown, 0F);
            return;
        }
        this.draw(state, camera, partialTicks, HALO_SIZE, HALO_ALPHA * shown, 0F);
        this.draw(state, camera, partialTicks, CORE_SIZE, CORE_ALPHA * shown, CORE_WHITE);
        if (PointLights.fireflyLightsOn())
            PointLights.add(
                    Mth.lerp(partialTicks, this.xo, this.x),
                    Mth.lerp(partialTicks, this.yo, this.y),
                    Mth.lerp(partialTicks, this.zo, this.z),
                    this.red, this.green, this.blue, LIGHT_STRENGTH * shown, LIGHT_RADIUS);
    }

    private void draw(QuadParticleRenderState state, Camera camera, float partialTicks, float size, float alpha, float white) {
        this.quadSize = size / 2F;
        this.alpha = alpha;
        this.setColor(Mth.lerp(white, this.red, 1F), Mth.lerp(white, this.green, 1F), Mth.lerp(white, this.blue, 1F));
        super.extract(state, camera, partialTicks);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public @NotNull SingleQuadParticle.Layer getLayer() {
        return FireflyParticle.LAYER;
    }
}
