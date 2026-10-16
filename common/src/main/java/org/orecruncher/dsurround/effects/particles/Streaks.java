package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;

/**
 * Draws a particle as a streak: a thin quad from where it is back along the way it came, bright at its head and fading
 * to nothing at its tail, turned to face the camera across its length. For sparks and molten droplets.
 */
final class Streaks {

    private Streaks() {
    }

    /**
     * @param prevX     where it was last tick
     * @param x         where it is now
     * @param length    the tail's length, as a multiple of how far it moved in the last tick
     * @param halfWidth half the streak's width, in blocks
     * @param color     its red, green and blue, 0 to 1
     * @param alpha     its head's opacity; the tail fades to none
     */
    static void draw(VertexConsumer buffer, Camera camera, float partialTick,
                     double prevX, double prevY, double prevZ, double x, double y, double z,
                     float length, float halfWidth, float[] color, float alpha, int light,
                     float u0, float u1, float v0, float v1) {
        // Its head, relative to the camera, and its tail trailing back along the way it came
        var cam = camera.position();
        float hx = (float) (Mth.lerp(partialTick, prevX, x) - cam.x);
        float hy = (float) (Mth.lerp(partialTick, prevY, y) - cam.y);
        float hz = (float) (Mth.lerp(partialTick, prevZ, z) - cam.z);
        float tx = hx - (float) (x - prevX) * length;
        float ty = hy - (float) (y - prevY) * length;
        float tz = hz - (float) (z - prevZ) * length;

        // Across the streak: square to its length and the line to the camera
        float lx = hx - tx, ly = hy - ty, lz = hz - tz;
        float sx = ly * hz - lz * hy, sy = lz * hx - lx * hz, sz = lx * hy - ly * hx;
        float across = sx * sx + sy * sy + sz * sz;
        if (across < 1.0E-10F) {
            // Still, or coming straight at the camera: a dot facing it
            var left = camera.leftVector();
            sx = left.x();
            sy = left.y();
            sz = left.z();
            across = 1F;
        }
        float scale = halfWidth * Mth.invSqrt(across);
        sx *= scale;
        sy *= scale;
        sz *= scale;

        buffer.addVertex(hx - sx, hy - sy, hz - sz).setUv(u1, v1).setColor(color[0], color[1], color[2], alpha).setLight(light);
        buffer.addVertex(tx - sx, ty - sy, tz - sz).setUv(u1, v0).setColor(color[0], color[1], color[2], 0F).setLight(light);
        buffer.addVertex(tx + sx, ty + sy, tz + sz).setUv(u0, v0).setColor(color[0], color[1], color[2], 0F).setLight(light);
        buffer.addVertex(hx + sx, hy + sy, hz + sz).setUv(u0, v1).setColor(color[0], color[1], color[2], alpha).setLight(light);
    }
}
