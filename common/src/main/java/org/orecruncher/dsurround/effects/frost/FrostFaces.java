package org.orecruncher.dsurround.effects.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Consumer;

/**
 * Finds the sides of a window that frost: the outside of the glass. A side is outside when it is open in front and
 * either nothing above that stops the rain, or more sky light reaches it than the other side (under eaves, or a porch
 * roof). So a window in a house wall frosts on the outside only, a free standing pane on both sides, and glass between
 * two rooms, or in a greenhouse under its glass roof, on neither of the inner ones.
 * <p>
 * The glass is where the block's shape is: a pane's is in the middle of its block, and spans as much of it as the
 * pane's connections reach. Faces of the shape too small to show frost (a pane's edge) are left out, and so are those
 * hidden inside the shape (where two arms of a pane cross).
 */
public final class FrostFaces {

    /**
     * How far beyond a face its window's edges are looked for: frost grows in from the edges no further than this
     */
    static final float EDGE_REACH = 2F;
    // Just off the glass, so the glass doesn't show through
    static final float LIFT = 0.006F;
    // A face narrower or shorter than this is the edge of a pane, too thin to show frost
    static final double MIN_EXTENT = 3D / 16D;
    // How close two planes, or a face and a block's edge, are taken as the same
    private static final double SAME = 1.0E-4D;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private FrostFaces() {
    }

    /**
     * The sides of a window block that frost, each handed to {@code out}
     */
    public static void facesOf(IFrostWorld world, BlockPos pos, BlockState state, Consumer<FrostFace> out) {
        var blocks = world.blocks();
        List<AABB> boxes = null;
        for (var side : SIDES) {
            var front = pos.relative(side);
            if (!isOpen(world, front) || !isOutside(world, pos, side))
                continue;
            if (boxes == null) {
                boxes = state.getShape(blocks, pos).toAabbs();
                if (boxes.isEmpty())
                    return;
            }
            int light = world.packedLight(front);
            float skyShare = world.skyLight(front) / 15F;
            float temperature = world.temperature(front);
            for (var box : boxes) {
                if (extentAcross(box, side) < MIN_EXTENT || box.maxY - box.minY < MIN_EXTENT)
                    continue;
                double plane = plane(box, side);
                if (isCovered(boxes, box, side, plane))
                    continue;
                out.accept(face(world, pos, side, box, plane, light, skyShare, temperature));
            }
        }
    }

    /**
     * Whether a side of a window is its outside: in front of it, nothing overhead, or more sky light than behind it
     */
    static boolean isOutside(IFrostWorld world, BlockPos pos, Direction side) {
        var front = pos.relative(side);
        if (world.seesSky(front))
            return true;
        var back = pos.relative(side.getOpposite());
        int behind = isOpen(world, back) ? world.skyLight(back) : 0;
        return world.skyLight(front) > behind;
    }

    /**
     * Whether a block is open: nothing solid, and no fluid, that frost could be on the other side of
     */
    static boolean isOpen(IFrostWorld world, BlockPos pos) {
        var blocks = world.blocks();
        var state = blocks.getBlockState(pos);
        if (state.isAir())
            return true;
        return state.getFluidState().isEmpty() && state.getCollisionShape(blocks, pos).isEmpty();
    }

    private static FrostFace face(IFrostWorld world, BlockPos pos, Direction side, AABB box, double plane, int light, float skyShare, float temperature) {
        var across = acrossAxis(side);
        var lower = Direction.fromAxisAndDirection(across, Direction.AxisDirection.NEGATIVE);
        var upper = lower.getOpposite();
        int base = across == Direction.Axis.X ? pos.getX() : pos.getZ();
        float u0 = (float) (base + box.min(across));
        float u1 = (float) (base + box.max(across));
        float v0 = (float) (pos.getY() + box.minY);
        float v1 = (float) (pos.getY() + box.maxY);
        int planeBase = side.getAxis() == Direction.Axis.X ? pos.getX() : pos.getZ();
        float lifted = (float) (planeBase + plane) + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? LIFT : -LIFT);
        return new FrostFace(pos.getX(), pos.getY(), pos.getZ(), side, lifted, u0, u1, v0, v1,
                u0 - reach(world, pos, side, box, plane, lower),
                u1 + reach(world, pos, side, box, plane, upper),
                v0 - reach(world, pos, side, box, plane, Direction.DOWN),
                v1 + reach(world, pos, side, box, plane, Direction.UP),
                light, skyShare, temperature);
    }

    /**
     * How far the window carries on beyond a face, one way along the glass, up to {@link #EDGE_REACH}: through the
     * blocks next to it that are frosting glass, open in front, with glass in the same plane meeting this face's
     */
    static float reach(IFrostWorld world, BlockPos pos, Direction side, AABB box, double plane, Direction along) {
        if (!reachesEdge(box, along))
            return 0F;
        var blocks = world.blocks();
        var cursor = pos.mutable();
        var previous = box;
        float reach = 0F;
        while (reach < EDGE_REACH) {
            cursor.move(along);
            var state = blocks.getBlockState(cursor);
            if (!world.frosts(state) || !isOpen(world, cursor.relative(side)))
                break;
            var next = meeting(state.getShape(blocks, cursor).toAabbs(), previous, side, plane, along);
            if (next == null)
                break;
            reach += (float) (next.max(along.getAxis()) - next.min(along.getAxis()));
            if (!reachesEdge(next, along))
                break;
            previous = next;
        }
        return Math.min(reach, EDGE_REACH);
    }

    /**
     * The box of the next block's shape that carries a face on: its side in the same plane, starting at the edge the
     * face ends at, and alongside it
     */
    private static AABB meeting(List<AABB> boxes, AABB from, Direction side, double plane, Direction along) {
        var other = along.getAxis() == Direction.Axis.Y ? acrossAxis(side) : Direction.Axis.Y;
        for (var box : boxes) {
            if (Math.abs(plane(box, side) - plane) > SAME || !reachesEdge(box, along.getOpposite()))
                continue;
            if (box.max(other) > from.min(other) + SAME && box.min(other) < from.max(other) - SAME)
                return box;
        }
        return null;
    }

    /**
     * Whether a box reaches its block's edge one way
     */
    private static boolean reachesEdge(AABB box, Direction way) {
        var axis = way.getAxis();
        return way.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(axis) > 1D - SAME : box.min(axis) < SAME;
    }

    /**
     * Whether just in front of a box's side, at its middle, is inside another box of the shape
     */
    private static boolean isCovered(List<AABB> boxes, AABB box, Direction side, double plane) {
        double step = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.01D : -0.01D;
        double x = side.getAxis() == Direction.Axis.X ? plane + step : (box.minX + box.maxX) / 2D;
        double y = (box.minY + box.maxY) / 2D;
        double z = side.getAxis() == Direction.Axis.Z ? plane + step : (box.minZ + box.maxZ) / 2D;
        for (var other : boxes) {
            if (other != box && other.contains(x, y, z))
                return true;
        }
        return false;
    }

    /**
     * Where a box's side is, along the axis the side looks along, within the block
     */
    private static double plane(AABB box, Direction side) {
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(side.getAxis()) : box.min(side.getAxis());
    }

    /**
     * How wide a box's side is, across it
     */
    private static double extentAcross(AABB box, Direction side) {
        var axis = acrossAxis(side);
        return box.max(axis) - box.min(axis);
    }

    /**
     * The axis across a side, level with the ground: x for a north or south side, z for a west or east one
     */
    static Direction.Axis acrossAxis(Direction side) {
        return side.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }
}
