package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.compat.BlockCompat;
import org.orecruncher.dsurround.lib.math.MathStuff;

/**
 * Where a sound is treated as coming from, for ray tracing.
 */
final class SoundGeometry {

    /**
     * How far a sound in a solid block or fluid is moved towards the player.
     */
    static final double OFFSET = 0.876D;

    private SoundGeometry() {
    }

    /**
     * A sound inside a solid block or a fluid is moved most of a block towards the player. Otherwise the position
     * is unchanged.
     */
    static Vec3 offsetPositionIfNeeded(final BlockGetter world, final Vec3 origin, final Vec3 target) {
        if (shouldOffset(world, BlockPos.containing(origin))) {
            var normal = origin.vectorTo(target).normalize();
            return MathStuff.addScaled(origin, normal, OFFSET);
        }
        return origin;
    }

    /**
     * Whether a sound at this block should be moved before tracing: rays starting inside it would hit it at once.
     * <ul>
     *   <li>A solid block (one with a collision shape) would muffle the sound it contains.</li>
     *   <li>A fluid is a surface to the rays (they trace fluids), so a ray starting inside one hits it at its own
     *       start: every reverb ray bounces in place and no ray reaches the player. Waterfall sounds are placed in
     *       the water. Waterlogged blocks count too.</li>
     * </ul>
     * Air of any kind (cave air included), plants, torches and the like are neither, so a sound in them stays put.
     */
    static boolean shouldOffset(final BlockGetter world, final BlockPos pos) {
        return BlockCompat.isSolid(world, pos, world.getBlockState(pos)) || !world.getFluidState(pos).isEmpty();
    }
}
