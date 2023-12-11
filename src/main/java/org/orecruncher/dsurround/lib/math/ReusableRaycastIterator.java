package org.orecruncher.dsurround.lib.math;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class ReusableRaycastIterator implements Iterator<BlockHitResult> {

    private final ReusableRaycastContext traceContext;
    private final BlockPos targetBlock;
    private final Vec3d normal;

    @Nullable
    private BlockHitResult hitResult;

    public ReusableRaycastIterator(final ReusableRaycastContext traceContext) {
        this.traceContext = traceContext;
        this.targetBlock = BlockPos.ofFloored(traceContext.getEnd());
        this.normal = traceContext.getStart().relativize(traceContext.getEnd()).normalize();
        doTrace();
    }

    private void doTrace() {
        if (this.hitResult != null && this.hitResult.getPos().equals(this.targetBlock)) {
            this.hitResult = null;
        } else {
            this.hitResult = this.traceContext.trace();
        }
    }

    @Override
    public boolean hasNext() {
        return this.hitResult != null && this.hitResult.getType() != HitResult.Type.MISS;
    }

    @Override
    public BlockHitResult next() {
        if (this.hitResult == null || this.hitResult.getType() == HitResult.Type.MISS)
            throw new IllegalStateException("No more blocks in trace");
        var result = this.hitResult;
        this.traceContext.setStart(this.hitResult.getPos().add(this.normal));
        doTrace();
        return result;
    }

}