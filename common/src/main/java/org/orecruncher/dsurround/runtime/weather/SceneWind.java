package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * The wind around the player, for effects to lean with: waterfall mist, steam, breath. Set each tick by the
 * {@link WindService}; read by particles, which are made far from anything the service could be handed to. Before the
 * first tick, and out of the game, it is still air.
 * <p>
 * Effects lean only as far as they are out under the open sky: a cave or a building keeps the wind off. Client
 * thread only, which in Minecraft is also the render thread.
 */
public final class SceneWind {

    /**
     * How much of the wind's speed something light, like mist or steam, is carried at, once it has caught it
     */
    public static final float CARRIED_SHARE = 0.5F;
    // Sky light below which there is no wind, and the range over which it builds to full, as for the weather's fog
    static final int MIN_SKY_LIGHT = 8;
    static final float SKY_LIGHT_RANGE = 7F;

    private static Wind wind;
    private static double originX;
    private static double originZ;

    private SceneWind() {
    }

    /**
     * The wind now, and where the player is: a gust passes across the area from upwind of them
     */
    static void update(Wind current, double cameraX, double cameraZ) {
        wind = current;
        originX = cameraX;
        originZ = cameraZ;
    }

    static void clear() {
        wind = null;
    }

    /**
     * The wind at a place, east-west and north-south, in blocks a tick: the steady wind, stronger where a gust is
     * passing
     */
    public static float x(double x, double z) {
        var current = wind;
        return current == null ? 0F : current.x() * gustAt(current, x, z);
    }

    public static float z(double x, double z) {
        var current = wind;
        return current == null ? 0F : current.z() * gustAt(current, x, z);
    }

    private static float gustAt(Wind current, double x, double z) {
        var along = (float) ((x - originX) * current.directionX() + (z - originZ) * current.directionZ());
        return current.gustFactor(along, 0F);
    }

    /**
     * How much of the wind reaches a place, 0 to 1: all of it out under the open sky, none in a cave or a building
     */
    public static float exposure(Level level, BlockPos pos) {
        return exposure(level.getBrightness(LightLayer.SKY, pos));
    }

    static float exposure(int skyLight) {
        return Mth.clamp((skyLight - MIN_SKY_LIGHT) / SKY_LIGHT_RANGE, 0F, 1F);
    }

    /**
     * The push a tick that carries something with the given drag at {@link #CARRIED_SHARE} of the wind's speed, once
     * it has caught it: against drag that keeps {@code friction} of its speed each tick, it settles where the push makes
     * up what the drag takes
     *
     * @param wind     the wind's speed this way, from {@link #x} or {@link #z}
     * @param exposure from {@link #exposure}
     * @param friction the share of its speed it keeps each tick, as a particle's friction
     */
    public static float push(float wind, float exposure, float friction) {
        return wind * CARRIED_SHARE * exposure * (1F - friction);
    }
}
