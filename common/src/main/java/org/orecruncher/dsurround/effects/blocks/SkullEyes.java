package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Where a placed skull's eyes are, from its block's corner, worked out as the skull renderer places the head: an
 * 8 pixel cube with its bottom on the floor at the block's middle (a skull on the floor, turned by its rotation), or
 * its bottom 4 pixels up and pushed 4 pixels back toward the wall (a skull on a wall, its face at the block's middle).
 * <p>
 * On the skeleton and wither skeleton faces, each eye is 2 pixels wide and 1 high, 2 pixels either side of the middle,
 * on the face's fifth row down: 3.5 pixels above the bottom of the head.
 *
 * @param left    one eye, just in front of the face
 * @param right   the other eye
 * @param light   where the eyes' light comes from: out in front of the face, between the eyes
 * @param facing  the way the face looks, level
 * @param lateral across the face, from one eye to the other, level
 */
public record SkullEyes(Vec3 left, Vec3 right, Vec3 light, Vec3 facing, Vec3 lateral) {

    private static final double PIXEL = 1D / 16D;
    // From the head's middle to its face
    static final double FACE = 4 * PIXEL;
    // How far in front of the face the flames start, so they aren't drawn into it
    static final double IN_FRONT = 0.03D;
    // From the middle of the face to each eye
    static final double EYE_APART = 2 * PIXEL;
    // From the bottom of the head to the eyes
    static final double EYE_HEIGHT = 3.5D * PIXEL;
    // How far in front of the face the light is: right against it, it would wash the face out
    static final double LIGHT_OUT = 0.3D;
    // How far a skull on a wall is pushed back toward it, and raised
    static final double WALL_BACK = 4 * PIXEL;
    static final double WALL_UP = 4 * PIXEL;

    /**
     * The eyes of a skull on the floor ({@link SkullBlock#ROTATION}) or a wall ({@link WallSkullBlock#FACING}), or
     * empty for a block with neither.
     */
    public static Optional<SkullEyes> of(BlockState state) {
        if (state.hasProperty(SkullBlock.ROTATION))
            return Optional.of(onFloor(state.getValue(SkullBlock.ROTATION)));
        if (state.hasProperty(WallSkullBlock.FACING))
            return Optional.of(onWall(state.getValue(WallSkullBlock.FACING)));
        return Optional.empty();
    }

    /**
     * A skull on the floor, turned by its rotation segment (0 to 15)
     */
    static SkullEyes onFloor(int rotation) {
        return at(RotationSegment.convertToDegrees(rotation), 0.5D, 0D, 0.5D);
    }

    /**
     * A skull on a wall, looking out from it. The renderer turns it as a floor skull with the rotation segment that
     * looks away from the wall.
     */
    static SkullEyes onWall(Direction facing) {
        var degrees = RotationSegment.convertToDegrees(RotationSegment.convertToSegment(facing.getOpposite()));
        return at(degrees, 0.5D - facing.getStepX() * WALL_BACK, WALL_UP, 0.5D - facing.getStepZ() * WALL_BACK);
    }

    /**
     * A head turned by the renderer's angle, in degrees, with the middle of its bottom at the given point. The model's
     * face looks along -z; the renderer turns it about y, then mirrors x and y, so at 0 degrees it looks north and at
     * 180 south.
     */
    static SkullEyes at(double degrees, double x, double y, double z) {
        double angle = Math.toRadians(degrees);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        var facing = new Vec3(sin, 0D, -cos);
        var lateral = new Vec3(cos, 0D, sin);

        var eyes = new Vec3(x, y + EYE_HEIGHT, z).add(facing.scale(FACE + IN_FRONT));
        var light = new Vec3(x, y + EYE_HEIGHT, z).add(facing.scale(FACE + LIGHT_OUT));
        return new SkullEyes(eyes.add(lateral.scale(-EYE_APART)), eyes.add(lateral.scale(EYE_APART)), light, facing,
                lateral);
    }
}
