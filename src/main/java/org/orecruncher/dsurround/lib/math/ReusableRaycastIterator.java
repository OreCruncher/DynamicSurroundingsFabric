package org.orecruncher.dsurround.lib.math;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class ReusableRaycastIterator implements Iterator<BlockHitResult> {

    private final ReusableRaycastContext traceContext;
    private final BlockPos targetBlock;
    private final Vec3 normal;

    @Nullable
    private BlockHitResult hitResult;

    public ReusableRaycastIterator(final ReusableRaycastContext traceContext) {
        this.traceContext = traceContext;
        this.targetBlock = BlockPos.containing(traceContext.getEnd());
        this.normal = traceContext.getStart().vectorTo(traceContext.getEnd()).normalize();
        doTrace();
    }

    private void doTrace() {
        if (this.hitResult != null && this.hitResult.getBlockPos().equals(this.targetBlock)) {
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
        this.traceContext.setStart(this.hitResult.getLocation().add(this.normal));
        doTrace();
        return result;
    }

}