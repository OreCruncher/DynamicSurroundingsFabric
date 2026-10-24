package org.orecruncher.dsurround.effects.lightning;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the shape of a bolt ({@link BoltShape}). Each line is a straight one split in two again and again, each middle
 * pushed aside at random by a share of that piece's length, which gives the jagged, wandering look of a lightning
 * channel at every scale. Branches fork from the main channel at an angle, and fork again. How strong the storm is
 * ({@code intensity}, 0 to 1) sets how many branches there are and how many times they fork.
 */
public final class BoltGenerator {

    /**
     * How far the middle of a piece is pushed aside when it is split, at most, as a share of its length
     */
    static final float ROUGHNESS = 0.2F;
    /**
     * Pieces are split until they are shorter than this, in blocks
     */
    static final float FINEST = 2.5F;
    // At most 2^7 pieces to a line
    static final int MAX_SPLITS = 7;
    /**
     * The most points in a bolt; no more branches are added once it has this many
     */
    static final int MAX_POINTS = 1500;
    // Branches fork from no further along their parent than this, so the end of a channel is a single point
    private static final float FORK_REACH = 0.85F;

    private BoltGenerator() {
    }

    /**
     * Cloud to ground: from where the bolt starts, in the cloud, down to {@code (dx, dy, dz)} from there, on the
     * ground. Its branches reach downward too, and stop short of the ground.
     */
    public static BoltShape groundStrike(float dx, float dy, float dz, float intensity, IRandomizer random) {
        return build(dx, dy, dz, intensity, true, random);
    }

    /**
     * Spider lightning: crawling sideways under the cloud base, from where it starts to {@code (dx, dy, dz)} from
     * there, its branches spreading out level with it.
     */
    public static BoltShape spider(float dx, float dy, float dz, float intensity, IRandomizer random) {
        return build(dx, dy, dz, intensity, false, random);
    }

    /**
     * How many branches deep a bolt goes: 1 in a weak storm, up to 3 in the strongest
     */
    static int maxDepth(float intensity) {
        return 1 + Mth.clamp((int) (Mth.clamp(intensity, 0F, 1F) * 3F), 0, 2);
    }

    /**
     * How many branches fork from the main channel, on average: 2 in a weak storm, up to 10 in the strongest
     */
    static float mainBranches(float intensity) {
        return Mth.lerp(Mth.clamp(intensity, 0F, 1F), 2F, 10F);
    }

    /**
     * How many branches fork from a branch {@code depth} deep, on average
     */
    static float subBranches(float intensity, int depth) {
        return Mth.clamp(intensity, 0F, 1F) * 3F / depth;
    }

    private static BoltShape build(float dx, float dy, float dz, float intensity, boolean downward, IRandomizer random) {
        var channels = new ArrayList<BoltShape.Channel>();
        var mainPoints = line(0F, 0F, 0F, dx, dy, dz, random);
        float mainLength = pathLength(mainPoints);
        var main = new BoltShape.Channel(mainPoints, arrivals(mainPoints, 0F, mainLength), 0);
        channels.add(main);
        int count = round(mainBranches(intensity) * random.nextFloat(0.6F, 1.4F), random);
        addBranches(channels, main, count, mainLength, intensity, maxDepth(intensity), downward, random);
        return new BoltShape(channels);
    }

    private static void addBranches(List<BoltShape.Channel> channels, BoltShape.Channel parent, int count, float mainLength,
                                    float intensity, int maxDepth, boolean downward, IRandomizer random) {
        int n = parent.size();
        if (n < 3)
            return;
        float parentLength = pathLength(parent.points());
        for (int b = 0; b < count; b++) {
            if (pointCount(channels) >= MAX_POINTS)
                return;
            // Somewhere along the parent, not at either end
            int at = 1 + random.nextInt(Math.max(1, (int) ((n - 1) * FORK_REACH) - 1));

            // Which way the parent runs there
            float tx = parent.x(at + 1) - parent.x(at - 1);
            float ty = parent.y(at + 1) - parent.y(at - 1);
            float tz = parent.z(at + 1) - parent.z(at - 1);
            float tl = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1.0E-4F)
                continue;
            tx /= tl;
            ty /= tl;
            tz /= tl;

            // Turned aside from it by 20 to 50 degrees, around a direction picked at random
            var side = perpendicular(tx, ty, tz, random);
            float angle = random.nextFloat(0.35F, 0.9F);
            float cos = Mth.cos(angle), sin = Mth.sin(angle);
            float bx = tx * cos + side[0] * sin;
            float by = ty * cos + side[1] * sin;
            float bz = tz * cos + side[2] * sin;
            // A ground strike's branches keep falling; spider lightning's spread out level, under the cloud base, on
            // the heading they were turned to (or any, where the parent ran straight down there)
            if (downward) {
                by = Math.min(by, -0.25F);
            } else {
                float heading = Mth.sqrt(bx * bx + bz * bz);
                if (heading < 0.1F) {
                    float any = random.nextFloat() * Mth.TWO_PI;
                    bx = Mth.cos(any);
                    bz = Mth.sin(any);
                } else {
                    bx /= heading;
                    bz /= heading;
                }
                by = Mth.clamp(by, -0.3F, 0.05F);
            }
            float bl = Mth.sqrt(bx * bx + by * by + bz * bz);
            bx /= bl;
            by /= bl;
            bz /= bl;

            float length = Math.max(FINEST * 2F, parentLength * random.nextFloat(0.15F, 0.45F));
            float sx = parent.x(at), sy = parent.y(at), sz = parent.z(at);
            var points = line(sx, sy, sz, sx + bx * length, sy + by * length, sz + bz * length, random);
            var branch = new BoltShape.Channel(points, arrivals(points, parent.arrival()[at], mainLength), parent.depth() + 1);
            channels.add(branch);

            if (branch.depth() < maxDepth) {
                int sub = round(subBranches(intensity, branch.depth()) * random.nextFloat(0.5F, 1.5F), random);
                addBranches(channels, branch, sub, mainLength, intensity, maxDepth, downward, random);
            }
        }
    }

    /**
     * A jagged line from a to b: x, y, z of each point, a and b included
     */
    static float[] line(float ax, float ay, float az, float bx, float by, float bz, IRandomizer random) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        int splits = length <= FINEST ? 0 : Mth.clamp(Mth.ceil(Math.log(length / FINEST) / Math.log(2)), 0, MAX_SPLITS);
        int pieces = 1 << splits;
        var points = new float[(pieces + 1) * 3];
        points[0] = ax;
        points[1] = ay;
        points[2] = az;
        points[pieces * 3] = bx;
        points[pieces * 3 + 1] = by;
        points[pieces * 3 + 2] = bz;
        displace(points, 0, pieces, random);
        return points;
    }

    // Sets the point midway between i and j, pushed aside, then does the same for each half
    private static void displace(float[] points, int i, int j, IRandomizer random) {
        if (j - i < 2)
            return;
        int m = (i + j) >>> 1;
        float ax = points[i * 3], ay = points[i * 3 + 1], az = points[i * 3 + 2];
        float bx = points[j * 3], by = points[j * 3 + 1], bz = points[j * 3 + 2];
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float push = 0F;
        float[] side = {0F, 0F, 0F};
        if (length > 1.0E-4F) {
            side = perpendicular(dx / length, dy / length, dz / length, random);
            push = length * ROUGHNESS * (random.nextFloat() * 2F - 1F);
        }
        points[m * 3] = (ax + bx) * 0.5F + side[0] * push;
        points[m * 3 + 1] = (ay + by) * 0.5F + side[1] * push;
        points[m * 3 + 2] = (az + bz) * 0.5F + side[2] * push;
        displace(points, i, m, random);
        displace(points, m, j, random);
    }

    /**
     * A direction at right angles to the given one (which must be of length 1), picked at random
     */
    static float[] perpendicular(float tx, float ty, float tz, IRandomizer random) {
        for (int tries = 0; tries < 4; tries++) {
            float rx = random.nextFloat() * 2F - 1F;
            float ry = random.nextFloat() * 2F - 1F;
            float rz = random.nextFloat() * 2F - 1F;
            float along = rx * tx + ry * ty + rz * tz;
            rx -= along * tx;
            ry -= along * ty;
            rz -= along * tz;
            float l = Mth.sqrt(rx * rx + ry * ry + rz * rz);
            if (l > 0.05F)
                return new float[]{rx / l, ry / l, rz / l};
        }
        // Unlucky: any direction at right angles will do
        float[] axis = Math.abs(ty) < 0.9F ? new float[]{0F, 1F, 0F} : new float[]{1F, 0F, 0F};
        float cx = ty * axis[2] - tz * axis[1];
        float cy = tz * axis[0] - tx * axis[2];
        float cz = tx * axis[1] - ty * axis[0];
        float l = Mth.sqrt(cx * cx + cy * cy + cz * cz);
        return new float[]{cx / l, cy / l, cz / l};
    }

    /**
     * When the leader reaches each point: {@code start} at the first, then on by the distance along the line, as a
     * share of the main channel's length
     */
    static float[] arrivals(float[] points, float start, float mainLength) {
        int n = points.length / 3;
        var arrival = new float[n];
        arrival[0] = start;
        float scale = mainLength > 0F ? 1F / mainLength : 0F;
        for (int i = 1; i < n; i++) {
            float dx = points[i * 3] - points[i * 3 - 3];
            float dy = points[i * 3 + 1] - points[i * 3 - 2];
            float dz = points[i * 3 + 2] - points[i * 3 - 1];
            arrival[i] = arrival[i - 1] + Mth.sqrt(dx * dx + dy * dy + dz * dz) * scale;
        }
        return arrival;
    }

    static float pathLength(float[] points) {
        float length = 0F;
        for (int i = 3; i < points.length; i += 3) {
            float dx = points[i] - points[i - 3];
            float dy = points[i + 1] - points[i - 2];
            float dz = points[i + 2] - points[i - 1];
            length += Mth.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return length;
    }

    private static int pointCount(List<BoltShape.Channel> channels) {
        int count = 0;
        for (var channel : channels)
            count += channel.size();
        return count;
    }

    // Rounded up or down at random, by its fraction: 2.3 is 3 three times in ten
    private static int round(float value, IRandomizer random) {
        int whole = (int) value;
        return whole + (random.nextFloat() < value - whole ? 1 : 0);
    }
}
