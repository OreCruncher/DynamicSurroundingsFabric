package org.orecruncher.dsurround.lib.math;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
 * <p>
 * An instance can be reused for another ray with {@link #restart(Vec3, Vec3)}, so one can be kept along with its
 * context (one per thread: it isn't thread safe).
 */
public class ReusableRaycastIterator implements Iterator<BlockHitResult> {

    private final ReusableRaycastContext traceContext;
    private final BlockPos.MutableBlockPos targetBlock = new BlockPos.MutableBlockPos();
    private Vec3 end = Vec3.ZERO;
    private Vec3 normal = Vec3.ZERO;
    private int stepsRemaining;

    @Nullable
    private BlockHitResult hitResult;

    /**
     * An iterator along the context's current ray, starting now.
     */
    public ReusableRaycastIterator(final ReusableRaycastContext traceContext) {
        this.traceContext = traceContext;
        this.restart();
    }

    // For unstarted(): the parameter only tells this constructor apart from the public one
    private ReusableRaycastIterator(final ReusableRaycastContext traceContext, boolean unstarted) {
        this.traceContext = traceContext;
    }

    /**
     * An iterator that isn't started, for keeping with a context that has no world yet. It has no hits until
     * {@link #restart(Vec3, Vec3)}.
     */
    public static ReusableRaycastIterator unstarted(final ReusableRaycastContext traceContext) {
        return new ReusableRaycastIterator(traceContext, false);
    }

    /**
     * Starts again along a new ray, from {@code start} towards {@code end}, using the context's world.
     *
     * @return this iterator
     */
    public ReusableRaycastIterator restart(final Vec3 start, final Vec3 end) {
        this.traceContext.setRay(start, end);
        return this.restart();
    }

    private ReusableRaycastIterator restart() {
        this.end = this.traceContext.getEnd();
        this.targetBlock.set(Mth.floor(this.end.x), Mth.floor(this.end.y), Mth.floor(this.end.z));
        var ray = this.traceContext.getStart().vectorTo(this.end);
        this.normal = ray.normalize();
        // Each step moves the start at least one block along the ray, so this is more than enough
        this.stepsRemaining = (int) Math.ceil(ray.length()) + 2;
        this.hitResult = this.traceContext.trace();
        return this;
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
