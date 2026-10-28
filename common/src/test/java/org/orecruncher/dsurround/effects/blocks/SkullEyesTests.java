package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for where a placed skull's eyes are.
 */
public class SkullEyesTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final double EPSILON = 1e-6D;
    private static final double EYE_Y = 3.5D / 16D;

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON, "x of " + actual);
        assertEquals(expected.y, actual.y, EPSILON, "y of " + actual);
        assertEquals(expected.z, actual.z, EPSILON, "z of " + actual);
    }

    @Test
    void floorSkullPlacedLookingNorthFacesSouthToThePlayer() {
        // SkullBlock takes its rotation from the placing player's yaw; north is 180
        var eyes = SkullEyes.onFloor(RotationSegment.convertToSegment(180F));
        assertVec(new Vec3(0, 0, 1), eyes.facing());
        double z = 0.5D + 0.25D + SkullEyes.IN_FRONT;
        var low = eyes.left().x < eyes.right().x ? eyes.left() : eyes.right();
        var high = eyes.left().x < eyes.right().x ? eyes.right() : eyes.left();
        assertVec(new Vec3(0.375D, EYE_Y, z), low);
        assertVec(new Vec3(0.625D, EYE_Y, z), high);
    }

    @Test
    void floorSkullQuarterTurns() {
        assertVec(new Vec3(0, 0, -1), SkullEyes.onFloor(0).facing());
        assertVec(new Vec3(1, 0, 0), SkullEyes.onFloor(4).facing());
        assertVec(new Vec3(0, 0, 1), SkullEyes.onFloor(8).facing());
        assertVec(new Vec3(-1, 0, 0), SkullEyes.onFloor(12).facing());
    }

    @Test
    void everyFloorRotationKeepsTheEyesOnTheFaceInsideTheBlock() {
        var middle = new Vec3(0.5D, EYE_Y, 0.5D);
        for (int rotation = 0; rotation < 16; rotation++) {
            var eyes = SkullEyes.onFloor(rotation);
            assertEquals(0.25D, eyes.left().distanceTo(eyes.right()), EPSILON, "eyes apart at " + rotation);
            assertEquals(0D, eyes.facing().dot(eyes.lateral()), EPSILON, "across the face at " + rotation);
            for (var eye : new Vec3[]{eyes.left(), eyes.right()}) {
                assertEquals(EYE_Y, eye.y, EPSILON);
                assertTrue(eye.x > 0D && eye.x < 1D && eye.z > 0D && eye.z < 1D, "inside the block at " + rotation);
                // Just in front of the face, the same distance out along where it looks
                assertEquals(0.25D + SkullEyes.IN_FRONT, eye.subtract(middle).dot(eyes.facing()), EPSILON);
            }
            assertEquals(0.25D + SkullEyes.LIGHT_OUT, eyes.light().subtract(middle).dot(eyes.facing()), EPSILON,
                    "light out in front at " + rotation);
        }
    }

    @Test
    void wallSkullLooksOutFromTheWallWithItsFaceAtTheMiddle() {
        for (var facing : Direction.Plane.HORIZONTAL)
            assertWallSkull(facing);
    }

    private static void assertWallSkull(Direction facing) {
        var eyes = SkullEyes.onWall(facing);
        var step = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        assertVec(step, eyes.facing());
        var middle = new Vec3(0.5D, 0.25D + EYE_Y, 0.5D);
        for (var eye : new Vec3[]{eyes.left(), eyes.right()}) {
            var offset = eye.subtract(middle);
            assertEquals(SkullEyes.IN_FRONT, offset.dot(step), EPSILON, "face at the block's middle");
            assertEquals(0.125D, Math.abs(offset.dot(eyes.lateral())), EPSILON);
            assertEquals(0D, offset.y, EPSILON);
        }
    }

    @Test
    void readsTheSkullsBlockState() {
        var floor = Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, 4);
        assertVec(new Vec3(1, 0, 0), SkullEyes.of(floor).orElseThrow().facing());

        var wall = Blocks.WITHER_SKELETON_WALL_SKULL.defaultBlockState().setValue(WallSkullBlock.FACING, Direction.WEST);
        assertVec(new Vec3(-1, 0, 0), SkullEyes.of(wall).orElseThrow().facing());

        assertTrue(SkullEyes.of(Blocks.STONE.defaultBlockState()).isEmpty());
    }
}
