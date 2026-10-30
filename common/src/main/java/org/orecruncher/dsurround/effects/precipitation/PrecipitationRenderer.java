package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws a {@link PrecipitationField}: rain as thin streaks along the way each drop falls, slanted by the wind and
 * turned toward the camera, snow as flakes facing it, turning slowly as they fall, and dust as large faint puffs the
 * color of the ground it was blown from. The streaks are a plain color that fades out toward their tail; the flakes
 * are cut from the game's own snow texture ({@link Snowflakes}), and the puffs are sprites from the particle atlas.
 * All are lit by the lightmap, like vanilla's weather. Also draws {@link DampSpots} and {@link SnowFlecks}, flat on
 * the ground. Positions are
 * relative to the camera, as for particles and vanilla's weather. Render thread only, but for {@link #build}, which
 * {@link DropMeshes} runs on the drops' worker thread.
 */
final class PrecipitationRenderer {

    // A rain streak's length, half width and alpha: for the smallest drop, and how much more for the largest. Small
    // drops are shown smaller mostly by being shorter and fainter: much thinner than the smallest width, a streak a few
    // blocks off is under a pixel wide and flickers.
    static final float RAIN_MIN_LENGTH = 0.35F;
    static final float RAIN_LENGTH_RANGE = 0.65F;
    static final float RAIN_MIN_HALF_WIDTH = 0.015F;
    static final float RAIN_HALF_WIDTH_RANGE = 0.015F;
    static final float RAIN_MIN_ALPHA = 0.25F;
    static final float RAIN_ALPHA_RANGE = 0.25F;
    // A flake's half size: the smallest, and how much more the largest is. Most are small (see snowSize).
    static final float SNOW_MIN_HALF_SIZE = 0.05F;
    static final float SNOW_HALF_SIZE_RANGE = 0.07F;
    static final float SNOW_ALPHA = 0.9F;
    // How fast a flake turns, as a fraction of how fast it sways: about half a turn a second
    static final float SNOW_SPIN = 0.25F;
    // A puff of dust's half size: the smallest, and how much more the largest is
    static final float DUST_MIN_HALF_SIZE = 0.5F;
    static final float DUST_HALF_SIZE_RANGE = 0.6F;
    // Faint: the air fills with many overlapping puffs
    static final float DUST_ALPHA = 0.22F;
    // Dust is paler than the ground it is blown from: this fraction of the way to white
    static final float DUST_PALENESS = 0.25F;
    // How fast a puff turns, as a fraction of how fast it rises and falls
    static final float DUST_SPIN = 0.1F;
    /**
     * Puffs nearer the camera than this, in blocks, are not drawn, and fade in over {@link #DUST_NEAR_FADE} beyond it.
     * A puff close by covers much of the screen: in a storm those within 2 blocks were about a quarter of the dust's
     * drawing, for a haze the dust storm's fog already gives. Fading them also keeps big blobs from sliding across the
     * view.
     */
    static final float DUST_NEAR_HIDDEN = 2F;
    static final float DUST_NEAR_FADE = 1.5F;
    /**
     * How far behind the camera a drop can be and still reach into view, beyond the largest puff's half size, the
     * length of the longest streak or the largest flake's half size: room to spare. See {@link #behindMargin}.
     */
    static final float BEHIND_SPARE = 0.9F;
    /**
     * Rain and snow far off are thinned out: all are drawn out to this share of the way to the edge of the cylinder,
     * then a share falling off steadily to {@link #THIN_KEEP_AT_EDGE} at the edge. Far drops are small on screen and
     * partly fogged, and the outer ring holds most of them, so this spares about a third of the drawing. Dust, far
     * fewer and making the haze, is not thinned.
     */
    static final float THIN_FROM_SHARE = 1F / 3F;
    static final float THIN_KEEP_AT_EDGE = 0.2F;
    // A drop being left out, or let back in, as the camera moves fades over this much of the share, rather than popping
    static final float THIN_FADE = 0.1F;
    // A texture coordinate the drops shader takes as "no texture", for the rain streaks
    static final float NO_TEXTURE = -1F;
    // Drops this close to the cylinder's edge fade out, so they don't vanish all at once as the camera moves
    static final float EDGE_FADE = 2F;
    // A drop on a window: the bead's half size, for the smallest drop and how much more for the largest, and alpha;
    // its trail's width as a share of the bead's, and alpha as a share of the bead's at its bottom
    static final float STREAK_BEAD_MIN_HALF_SIZE = 0.018F;
    static final float STREAK_BEAD_HALF_SIZE_RANGE = 0.017F;
    static final float STREAK_BEAD_ALPHA = 0.6F;
    static final float STREAK_TRAIL_WIDTH = 0.45F;
    static final float STREAK_TRAIL_ALPHA = 0.5F;
    // A damp spot's color where the ground can't be darkened, so it is laid over it instead
    static final float DAMP_RED = 0.08F, DAMP_GREEN = 0.08F, DAMP_BLUE = 0.1F;

    private PrecipitationRenderer() {
    }

    /**
     * Where the camera is and which way it faces, for building the drops: a copy, as the camera's own vectors change
     * with it, so a frame's drops can be built away from the render thread
     */
    record View(Vec3 position, Vector3f left, Vector3f up, Vector3f look) {

        static View of(Camera camera) {
            return new View(camera.position(), new Vector3f(camera.leftVector()), new Vector3f(camera.upVector()),
                    new Vector3f(camera.forwardVector()));
        }

        /**
         * Whether the camera is where this was taken, facing the same way
         */
        boolean isOf(Camera camera) {
            return this.position.equals(camera.position()) && same(this.left, camera.leftVector())
                    && same(this.up, camera.upVector()) && same(this.look, camera.forwardVector());
        }

        private static boolean same(Vector3f a, Vector3fc b) {
            return a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
        }
    }

    /**
     * Whether there is anything of the kind to draw
     *
     * @param dustOnly true for the dust, false for the rain and snow
     */
    static boolean hasAny(PrecipitationField field, boolean dustOnly) {
        var d = field.drops();
        return d.count > 0 && (dustOnly ? d.dustCount > 0 : d.dustCount < d.count);
    }

    /**
     * Writes the drops into {@code quads}, for drawing with a pipeline that takes {@link DefaultVertexFormat#PARTICLE}:
     * with the particle atlas bound for the dust, and the snow texture for rain and snow. The dust and the falling rain
     * and snow are drawn apart, as the dust is drawn soft and they are not. Written straight into memory rather than
     * through a BufferBuilder: there are thousands (see {@link ParticleQuads}). Reads only the drops drawn, so it may
     * run on another thread while the drops' next tick is stepped, but not while the step is finished and its drops
     * made the ones drawn (see {@link DropMeshes}).
     *
     * @param dustOnly   true to draw just the dust; false to draw just the rain and snow
     * @param snowflakes the flakes of the snow texture, which must be bound when drawing rain and snow; each falling
     *                   flake keeps one for its whole fall. Empty to draw plain flakes.
     * @param largest    the largest of them, in pixels: the others are drawn in proportion
     * @param dust       the dust sprites, from the particle atlas, which must be bound when drawing dust; each puff
     *                   keeps one
     * @param scales     how big rain and snow are drawn
     * @param rainColor  the rain's color
     */
    static void build(PrecipitationField field, ParticleQuads quads, View view, float partialTick, boolean dustOnly,
                      List<Snowflakes.Flake> snowflakes, int largest, TextureAtlasSprite[] dust, DropScales scales, RainColor rainColor) {
        var d = field.drops();
        quads.begin(dustOnly ? d.dustCount : d.count - d.dustCount);
        if (!hasAny(field, dustOnly))
            return;

        var cam = view.position();
        Vector3fc left = view.left();
        Vector3fc up = view.up();
        Vector3fc look = view.look();
        float radius = field.radius();
        float margin = behindMargin(scales);
        // How much further each snowflake and puff has turned since its last tick: the same for all of a kind, so
        // worked out once, and added to each one's own turn (see PrecipitationField.spinCos)
        float snowTurn = partialTick * PrecipitationField.SNOW_SWAY_RATE * SNOW_SPIN;
        float snowCos = Mth.cos(snowTurn), snowSin = Mth.sin(snowTurn);
        float dustTurn = partialTick * PrecipitationField.DUST_TURBULENCE_RATE * DUST_SPIN;
        float dustCos = Mth.cos(dustTurn), dustSin = Mth.sin(dustTurn);
        float rainRed = rainColor.red(), rainGreen = rainColor.green(), rainBlue = rainColor.blue();
        // A streak's tail is clear
        int rainTail = ParticleQuads.color(rainRed, rainGreen, rainBlue, 0F);

        for (int i = 0; i < d.count; i++) {
            if ((d.kind[i] == PrecipitationField.DUST) != dustOnly)
                continue;
            float x = (float) (Mth.lerp(partialTick, d.prevX[i], d.x[i]) - cam.x);
            float y = (float) (Mth.lerp(partialTick, d.prevY[i], d.y[i]) - cam.y);
            float z = (float) (Mth.lerp(partialTick, d.prevZ[i], d.z[i]) - cam.z);
            if (isBehind(x, y, z, look, margin))
                continue;
            float distance = Mth.sqrt(x * x + z * z);
            float fade = field.fade(i, partialTick) * Mth.clamp((radius - distance) / EDGE_FADE, 0F, 1F);
            if (!dustOnly)
                fade *= thinned(d.thinning[i], distance, radius);
            else
                fade *= dustNearFade(x, y, z);
            if (fade <= 0F)
                continue;
            int light = d.light[i];

            if (d.kind[i] == PrecipitationField.DUST) {
                var sprite = dust[field.variant(i, dust.length)];
                float size = DUST_MIN_HALF_SIZE + DUST_HALF_SIZE_RANGE * field.dustSize(i);
                float cos = d.spinCos[i] * dustCos - d.spinSin[i] * dustSin;
                float sin = d.spinSin[i] * dustCos + d.spinCos[i] * dustSin;
                int c = d.color[i];
                float r = Mth.lerp(DUST_PALENESS, ((c >> 16) & 0xFF) / 255F, 1F);
                float g = Mth.lerp(DUST_PALENESS, ((c >> 8) & 0xFF) / 255F, 1F);
                float b = Mth.lerp(DUST_PALENESS, (c & 0xFF) / 255F, 1F);
                billboard(quads, x, y, z, left, up, cos, sin, size, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                        ParticleQuads.color(r, g, b, DUST_ALPHA * fade), light);
            } else if (d.kind[i] == PrecipitationField.SNOW) {
                float a = SNOW_ALPHA * fade;
                float cos = d.spinCos[i] * snowCos - d.spinSin[i] * snowSin;
                float sin = d.spinSin[i] * snowCos + d.spinCos[i] * snowSin;
                float size = (SNOW_MIN_HALF_SIZE + SNOW_HALF_SIZE_RANGE * field.snowSize(i)) * scales.snow();
                if (snowflakes.isEmpty()) {
                    billboard(quads, x, y, z, left, up, cos, sin, size, NO_TEXTURE, NO_TEXTURE, NO_TEXTURE, NO_TEXTURE, ParticleQuads.color(1F, 1F, 1F, a), light);
                } else {
                    // A flake of a pixel is drawn smaller than one of three, in proportion
                    var flake = snowflakes.get(field.variant(i, snowflakes.size()));
                    size *= flake.size() / (float) largest;
                    billboard(quads, x, y, z, left, up, cos, sin, size, flake.u0(), flake.v0(), flake.u1(), flake.v1(), ParticleQuads.color(1F, 1F, 1F, a), light);
                }
            } else {
                float dropSize = field.rainSize(i);
                float length = rainLength(dropSize, scales.rain());
                float halfWidth = rainHalfWidth(dropSize, scales.rain());
                // Along the way the drop is moving, its tail trailing up and upwind, turned about its own length to
                // face the camera
                float vx = d.windX[i], vy = -d.speed[i], vz = d.windZ[i];
                float inverse = Mth.invSqrt(vx * vx + vy * vy + vz * vz);
                vx *= inverse;
                vy *= inverse;
                vz *= inverse;
                // Across the streak: square to both its length and the line to the camera
                float sx = vy * z - vz * y, sy = vz * x - vx * z, sz = vx * y - vy * x;
                float across = sx * sx + sy * sy + sz * sz;
                if (across > 1.0E-6F) {
                    float scale = halfWidth * Mth.invSqrt(across);
                    sx *= scale;
                    sy *= scale;
                    sz *= scale;
                } else {
                    // Looking straight along the streak, where any turn will do
                    sx = left.x() * halfWidth;
                    sy = left.y() * halfWidth;
                    sz = left.z() * halfWidth;
                }
                float tx = x - vx * length, ty = y - vy * length, tz = z - vz * length;
                float a = (RAIN_MIN_ALPHA + RAIN_ALPHA_RANGE * dropSize) * fade;
                int head = ParticleQuads.color(rainRed, rainGreen, rainBlue, a);
                quads.vertex(x - sx, y - sy, z - sz, NO_TEXTURE, NO_TEXTURE, head, light);
                quads.vertex(tx - sx, ty - sy, tz - sz, NO_TEXTURE, NO_TEXTURE, rainTail, light);
                quads.vertex(tx + sx, ty + sy, tz + sz, NO_TEXTURE, NO_TEXTURE, rainTail, light);
                quads.vertex(x + sx, y + sy, z + sz, NO_TEXTURE, NO_TEXTURE, head, light);
            }
        }
    }

    /**
     * The share of the rain and snow drawn at a distance from the camera, horizontally: all of it near, falling off
     * steadily to {@link #THIN_KEEP_AT_EDGE} at the edge of the cylinder
     *
     * @param radius the cylinder's radius
     */
    static float shareDrawn(float distance, float radius) {
        float from = radius * THIN_FROM_SHARE;
        float across = (distance - from) / (radius - from);
        return Mth.lerp(Mth.clamp(across, 0F, 1F), 1F, THIN_KEEP_AT_EDGE);
    }

    /**
     * How much of a drop is drawn at a distance, 0 to 1: drops whose fixed random number is under the share drawn
     * there are drawn whole, those over it not at all, and those near it fade, so as the camera moves a drop slips out
     * of view rather than popping
     *
     * @param thinning the drop's own random number, 0 to 1
     * @param radius   the cylinder's radius
     */
    static float thinned(float thinning, float distance, float radius) {
        return Mth.clamp((shareDrawn(distance, radius) - thinning) / THIN_FADE + 1F, 0F, 1F);
    }

    /**
     * How much of a puff of dust is drawn for being near the camera, relative to which it is: none inside
     * {@link #DUST_NEAR_HIDDEN}, all of it {@link #DUST_NEAR_FADE} further out
     */
    static float dustNearFade(float x, float y, float z) {
        return Mth.clamp((Mth.sqrt(x * x + y * y + z * z) - DUST_NEAR_HIDDEN) / DUST_NEAR_FADE, 0F, 1F);
    }

    /**
     * A rain streak's length, for a drop of the given size, 0 to 1, drawn at the given scale
     */
    static float rainLength(float dropSize, float scale) {
        return (RAIN_MIN_LENGTH + RAIN_LENGTH_RANGE * dropSize) * scale;
    }

    /**
     * A rain streak's half width, for a drop of the given size, 0 to 1, drawn at the given scale. Smaller than usual
     * is shown by the streak being shorter only: thinner, a streak a few blocks off is under a pixel wide and
     * flickers. Larger widens it less than it lengthens it, so a big streak doesn't look like a rod.
     */
    static float rainHalfWidth(float dropSize, float scale) {
        return (RAIN_MIN_HALF_WIDTH + RAIN_HALF_WIDTH_RANGE * dropSize) * Mth.sqrt(Math.max(scale, 1F));
    }

    /**
     * How far behind the camera a drop can be and still reach into view, at the given scales: the largest of a puff's
     * half size, a streak's length and a flake's half size, with room to spare. Drops further behind are not drawn.
     */
    static float behindMargin(DropScales scales) {
        float dust = DUST_MIN_HALF_SIZE + DUST_HALF_SIZE_RANGE;
        float rain = rainLength(1F, scales.rain());
        float snow = (SNOW_MIN_HALF_SIZE + SNOW_HALF_SIZE_RANGE) * scales.snow();
        return Math.max(dust, Math.max(rain, snow)) + BEHIND_SPARE;
    }

    /**
     * Whether a drop, relative to the camera, is far enough behind it that nothing of it can be seen
     *
     * @param margin how far behind a drop can be and still reach into view (see {@link #behindMargin})
     */
    static boolean isBehind(float x, float y, float z, Vector3fc look, float margin) {
        return x * look.x() + y * look.y() + z * look.z() < -margin;
    }

    /**
     * A textured square facing the camera, turned in its plane by the angle whose cos and sin are given
     */
    private static void billboard(ParticleQuads quads, float x, float y, float z, Vector3fc left, Vector3fc up,
                                  float turnCos, float turnSin, float halfSize, float u0, float v0, float u1, float v1,
                                  int color, int light) {
        float cos = turnCos * halfSize, sin = turnSin * halfSize;
        float ax = left.x() * cos + up.x() * sin, ay = left.y() * cos + up.y() * sin, az = left.z() * cos + up.z() * sin;
        float bx = up.x() * cos - left.x() * sin, by = up.y() * cos - left.y() * sin, bz = up.z() * cos - left.z() * sin;
        quads.vertex(x - ax - bx, y - ay - by, z - az - bz, u1, v1, color, light);
        quads.vertex(x - ax + bx, y - ay + by, z - az + bz, u1, v0, color, light);
        quads.vertex(x + ax + bx, y + ay + by, z + az + bz, u0, v0, color, light);
        quads.vertex(x + ax - bx, y + ay - by, z + az - bz, u0, v1, color, light);
    }

    /**
     * Builds the snow flecks into a buffer and draws it with whatever shader is current, which should take
     * {@link DefaultVertexFormat#PARTICLE}, with the snow texture bound. Each lies flat on the face it landed on, the
     * top of a block or a side it was blown into, as the flake it fell as, at the flake's size, fading and shrinking as
     * it melts.
     *
     * @param snowflakes the flakes of the snow texture; empty to draw plain flecks
     * @param largest    the largest of them, in pixels: the others are drawn in proportion
     * @param scale      how big snowflakes are drawn
     */
    static void draw(SnowFlecks flecks, ByteBufferBuilder bytes, Consumer<MeshData> drawer, Camera camera, float partialTick, List<Snowflakes.Flake> snowflakes,
                     int largest, float scale) {
        if (flecks.count == 0)
            return;

        var cam = camera.position();
        BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.PARTICLE);

        for (int i = 0; i < flecks.count; i++) {
            float remaining = flecks.remaining(i, partialTick);
            if (remaining <= 0F)
                continue;
            float x = (float) (flecks.x[i] - cam.x);
            float y = (float) (flecks.y[i] - cam.y);
            float z = (float) (flecks.z[i] - cam.z);
            float half = (SNOW_MIN_HALF_SIZE + SNOW_HALF_SIZE_RANGE * flecks.size[i]) * scale * SnowFlecks.sizeFor(remaining);
            float u0 = NO_TEXTURE, v0 = NO_TEXTURE, u1 = NO_TEXTURE, v1 = NO_TEXTURE;
            if (!snowflakes.isEmpty()) {
                int variant = Mth.clamp((int) (flecks.variant[i] * snowflakes.size()), 0, snowflakes.size() - 1);
                var flake = snowflakes.get(variant);
                half *= flake.size() / (float) largest;
                u0 = flake.u0();
                v0 = flake.v0();
                u1 = flake.u1();
                v1 = flake.v1();
            }
            float a = SNOW_ALPHA * remaining;
            int light = flecks.light[i];
            // The corners, each a quarter turn from the last, as far out as a falling flake's (see billboard), in the
            // plane of the face it lies on
            float corner = half * Mth.SQRT_OF_TWO;
            float cu = flecks.turnCos[i] * corner, cv = flecks.turnSin[i] * corner;
            byte face = flecks.face[i];
            fleckCorner(builder, face, x, y, z, cu, cv).setUv(u0, v0).setColor(1F, 1F, 1F, a).setLight(light);
            fleckCorner(builder, face, x, y, z, -cv, cu).setUv(u0, v1).setColor(1F, 1F, 1F, a).setLight(light);
            fleckCorner(builder, face, x, y, z, -cu, -cv).setUv(u1, v1).setColor(1F, 1F, 1F, a).setLight(light);
            fleckCorner(builder, face, x, y, z, cv, -cu).setUv(u1, v0).setColor(1F, 1F, 1F, a).setLight(light);
        }

        var mesh = builder.build();
        if (mesh != null)
            drawer.accept(mesh);
    }

    /**
     * Builds the drops running down windows into a buffer and draws it with whatever shader is current, which should
     * take {@link DefaultVertexFormat#PARTICLE}. Untextured, in the rain's color: a bead, and above it the wet trail
     * it has left, fading toward its top.
     */
    static void draw(WindowStreaks streaks, ByteBufferBuilder bytes, Consumer<MeshData> drawer, Camera camera, float partialTick, RainColor rainColor) {
        if (streaks.count == 0)
            return;

        var cam = camera.position();
        BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.PARTICLE);

        for (int i = 0; i < streaks.count; i++) {
            float fade = streaks.fade(i, partialTick);
            if (fade <= 0F)
                continue;
            float x = (float) (streaks.x[i] - cam.x);
            float y = (float) (Mth.lerp(partialTick, streaks.prevY[i], streaks.y[i]) - cam.y);
            float z = (float) (streaks.z[i] - cam.z);
            float trailTop = (float) (Mth.lerp(partialTick, streaks.prevTrailTop[i], streaks.trailTop[i]) - cam.y);
            byte face = streaks.face[i];
            int light = streaks.light[i];
            float size = streaks.size[i];
            float half = STREAK_BEAD_MIN_HALF_SIZE + STREAK_BEAD_HALF_SIZE_RANGE * size;
            float a = STREAK_BEAD_ALPHA * fade;

            // The bead
            fleckCorner(builder, face, x, y, z, -half, -half).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), a).setLight(light);
            fleckCorner(builder, face, x, y, z, half, -half).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), a).setLight(light);
            fleckCorner(builder, face, x, y, z, half, half).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), a).setLight(light);
            fleckCorner(builder, face, x, y, z, -half, half).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), a).setLight(light);

            // The trail above it, narrower and fainter, fading out toward its top
            float trail = trailTop - y - half;
            if (trail > 0F) {
                float width = half * STREAK_TRAIL_WIDTH;
                float ta = a * STREAK_TRAIL_ALPHA;
                fleckCorner(builder, face, x, y, z, -width, half).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), ta).setLight(light);
                fleckCorner(builder, face, x, y, z, width, half).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), ta).setLight(light);
                fleckCorner(builder, face, x, y, z, width, half + trail).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), 0F).setLight(light);
                fleckCorner(builder, face, x, y, z, -width, half + trail).setUv(NO_TEXTURE, NO_TEXTURE).setColor(rainColor.red(), rainColor.green(), rainColor.blue(), 0F).setLight(light);
            }
        }

        var mesh = builder.build();
        if (mesh != null)
            drawer.accept(mesh);
    }

    /**
     * Adds a corner of something lying on a face, a snow fleck or a drop on a window, offset from its center by (u, v)
     * in the plane of the face: x and z on the top of a block, z and y on a west or east side, x and y on a north or
     * south side
     */
    private static VertexConsumer fleckCorner(BufferBuilder builder, byte face, float x, float y, float z, float u, float v) {
        return switch (face) {
            case SnowFlecks.FACE_WEST, SnowFlecks.FACE_EAST -> builder.addVertex(x, y + v, z + u);
            case SnowFlecks.FACE_NORTH, SnowFlecks.FACE_SOUTH -> builder.addVertex(x + u, y + v, z);
            default -> builder.addVertex(x + u, y, z + v);
        };
    }

    /**
     * Builds the damp spots into a buffer and draws it with whatever shader is current, which should take
     * {@link DefaultVertexFormat#POSITION_TEX_COLOR}.
     *
     * @param darkening true when the blend darkens the ground by the spot's alpha, so its color doesn't matter;
     *                  false to lay a dark color over the ground with an ordinary blend
     */
    static void draw(DampSpots spots, ByteBufferBuilder bytes, Consumer<MeshData> drawer, Camera camera, float partialTick, TextureAtlasSprite sprite, boolean darkening) {
        if (spots.count == 0)
            return;

        var cam = camera.position();
        float r = darkening ? 1F : DAMP_RED, g = darkening ? 1F : DAMP_GREEN, b = darkening ? 1F : DAMP_BLUE;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < spots.count; i++) {
            float a = spots.darkness(i, partialTick);
            if (a <= 0F)
                continue;
            float x = (float) (spots.x[i] - cam.x);
            float y = (float) (spots.y[i] - cam.y);
            float z = (float) (spots.z[i] - cam.z);
            // The corners, each a quarter turn from the last
            float cx = spots.cornerX[i], cz = spots.cornerZ[i];
            builder.addVertex(x + cx, y, z + cz).setUv(u0, v0).setColor(r, g, b, a);
            builder.addVertex(x - cz, y, z + cx).setUv(u0, v1).setColor(r, g, b, a);
            builder.addVertex(x - cx, y, z - cz).setUv(u1, v1).setColor(r, g, b, a);
            builder.addVertex(x + cz, y, z - cx).setUv(u1, v0).setColor(r, g, b, a);
        }

        var mesh = builder.build();
        if (mesh != null)
            drawer.accept(mesh);
    }
}
