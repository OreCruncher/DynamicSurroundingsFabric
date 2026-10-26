package org.orecruncher.dsurround.effects.entity;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.lib.random.Mixers;

import java.util.Arrays;

/**
 * The light a burning mob casts while the mod draws its fire (see {@link BurningEffect}): one flickering light at the
 * middle of its body, sized to it. One light for the whole fire, rather than a light for some of its tongues as a flame
 * jet has: each light is drawn over the part of the screen it may reach (all of it, when the camera is within its
 * reach, as it is with mobs chasing the player), so a crowd with several lights each costs a great deal.
 * <p>
 * Gathered as each mob's render state is extracted (from {@code MixinEntityRenderer}), once a frame however often a
 * mob is extracted, then added just before the lights are drawn. A very large crowd is held in check by {@link PointLights}' own limit,
 * which fades out the farthest lights rather than cutting them off. Render thread only.
 */
public final class BurningLight {

    // The flames' orange, as a tongue is through the middle of its life
    private static final float RED = 1F;
    private static final float GREEN = 0.55F;
    private static final float BLUE = 0.12F;
    // How bright it is, 0 to 1, on average, and how far either way it flickers
    static final float STRENGTH = 0.55F;
    static final float FLICKER = 0.2F;
    // How far it reaches, in blocks, for a zombie; bodies of other sizes in proportion to their flames
    static final float ZOMBIE_RADIUS = 3.5F;
    // The middle of the body, as a share of its height: a little low, where most of the flames are
    private static final double MIDDLE = 0.45D;

    // This frame's burning mobs, by entity id: where their light is, in the world, how bright and how far it reaches
    private static final Int2IntOpenHashMap SLOTS = new Int2IntOpenHashMap();
    private static double[] x = new double[16], y = new double[16], z = new double[16];
    private static float[] strength = new float[16], radius = new float[16];
    private static int count;
    // When the last was gathered, by System.nanoTime: lights gathered long before they come to be drawn (nothing drew
    // the lights in between) are left out rather than shown for mobs long gone
    private static long gathered;
    private static final long STALE_NANOS = 250_000_000L;

    static {
        SLOTS.defaultReturnValue(-1);
        PointLights.addSource(BurningLight::addGathered);
    }

    private BurningLight() {
    }

    /**
     * Notes the light of a burning entity whose fire the mod draws, if burning mobs' light is on. An entity drawn
     * again in the same frame (some mods draw entities more than once) updates its light rather than adding another.
     *
     * @param partialTick how far between ticks this frame is
     */
    public static void add(Entity entity, float partialTick) {
        if (!PointLights.flameLightsOn() || FeatureConflict.BURNING_MOB_LIGHT.isBlocked())
            return;
        int slot = SLOTS.get(entity.getId());
        if (slot < 0) {
            slot = count++;
            if (slot == x.length)
                grow();
            SLOTS.put(entity.getId(), slot);
        }
        x[slot] = Mth.lerp(partialTick, entity.xo, entity.getX());
        y[slot] = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() * MIDDLE;
        z[slot] = Mth.lerp(partialTick, entity.zo, entity.getZ());
        strength[slot] = flicker(entity.tickCount + partialTick, Mixers.fmix32(entity.getId()));
        radius[slot] = radiusFor(entity.getBbWidth());
        gathered = System.nanoTime();
    }

    private static void grow() {
        int size = x.length * 2;
        x = Arrays.copyOf(x, size);
        y = Arrays.copyOf(y, size);
        z = Arrays.copyOf(z, size);
        strength = Arrays.copyOf(strength, size);
        radius = Arrays.copyOf(radius, size);
    }

    /**
     * Adds the lights of the burning mobs gathered this frame, and starts the next frame afresh
     */
    private static void addGathered() {
        if (count == 0)
            return;
        if (System.nanoTime() - gathered <= STALE_NANOS) {
            for (int slot = 0; slot < count; slot++)
                PointLights.add(x[slot], y[slot], z[slot], RED, GREEN, BLUE, strength[slot], radius[slot]);
        }
        SLOTS.clear();
        count = 0;
    }

    /**
     * How far a burning mob's light reaches, for a body this wide: {@link #ZOMBIE_RADIUS} for a zombie, others in
     * proportion to the size of their flames
     */
    static float radiusFor(float width) {
        return ZOMBIE_RADIUS * BurningEffect.scaleFor(width) / BurningEffect.ZOMBIE_SCALE;
    }

    /**
     * How bright the light is at a moment: two waves of different speeds against each other, so it wavers rather than
     * pulses, started at a point of their own for each mob so a crowd doesn't flicker in step
     *
     * @param time in ticks
     * @param seed different for each mob
     */
    static float flicker(float time, int seed) {
        float phase = (seed & 0xFFFF) / 65536F * Mth.TWO_PI;
        float wave = 0.6F * Mth.sin(time * 1.3F + phase) + 0.4F * Mth.sin(time * 3.1F + phase * 1.7F);
        return STRENGTH + FLICKER * wave;
    }
}
