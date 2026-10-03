package org.orecruncher.dsurround.lib.math;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterates the blocks a ray hits, from the context's start towards its end. After each hit the ray continues from
 * one block-length further along it. A block the ray crosses for longer than that (e.g. on a diagonal) is reported
 * again, from inside, so the distances between hits measure the path through each block.
 * <p>
 * Iteration stops when the block containing the end is hit, when the next start would be at or past the end (a
 * trace from there would run backwards and could hit the same block again forever), or after enough steps to cover
 * the ray's length.
 */
public class ReusableRaycastIterator implements Iterator<BlockHitResult> {

    private final ReusableRaycastContext traceContext;
    private final BlockPos targetBlock;
    private final Vec3 end;
    private final Vec3 normal;
    private int stepsRemaining;

    @Nullable
    private BlockHitResult hitResult;

    public ReusableRaycastIterator(final ReusableRaycastContext traceContext) {
        this.traceContext = traceContext;
        this.end = traceContext.getEnd();
        this.targetBlock = BlockPos.containing(this.end);
        var ray = traceContext.getStart().vectorTo(this.end);
        this.normal = ray.normalize();
        // Each step moves the start at least one block along the ray, so this is more than enough
        this.stepsRemaining = (int) Math.ceil(ray.length()) + 2;
        this.hitResult = traceContext.trace();
    }

    @Override
    public boolean hasNext() {
        return this.hitResult != null && this.hitResult.getType() != HitResult.Type.MISS;
    }

    @Override
    public BlockHitResult next() {
        if (!this.hasNext())
            throw new NoSuchElementException("No more blocks in trace");
        var result = this.hitResult;
        this.hitResult = this.advance(result);
        return result;
    }

    /**
     * The hit after {@code current}, or null if iteration is over.
     */
    @Nullable
    private BlockHitResult advance(BlockHitResult current) {
        if (current.getBlockPos().equals(this.targetBlock) || --this.stepsRemaining <= 0)
            return null;

        var nextStart = current.getLocation().add(this.normal);
        // At or past the end: a trace from there would go backwards
        if (this.end.subtract(nextStart).dot(this.normal) <= 0)
            return null;

        this.traceContext.setStart(nextStart);
        return this.traceContext.trace();
    }
}
