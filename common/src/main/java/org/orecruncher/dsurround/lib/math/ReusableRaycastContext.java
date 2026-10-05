package org.orecruncher.dsurround.lib.math;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * A ClipContext whose start and end can be changed, so one instance can trace many rays. Traces use an empty
 * collision context (no entity), so they don't depend on the player and work on any thread that may read the
 * world.
 * <p>
 * The world can be changed too, so an instance can be kept and reused (one per thread: it isn't thread safe). One
 * kept that way should have its world cleared when not in use, or it keeps the world loaded in memory.
 */
public class ReusableRaycastContext extends ClipContext {

    @Nullable
    private BlockGetter world;

    public ReusableRaycastContext(@Nullable BlockGetter world, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling) {
        this(world, Vec3.ZERO, Vec3.ZERO, shapeType, fluidHandling);
    }

    public ReusableRaycastContext(@Nullable BlockGetter world, Vec3 start, Vec3 end, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling) {
        super(start, end, shapeType, fluidHandling, CollisionContext.empty());
        this.world = world;
    }

    /**
     * The world traced in; null to let go of it while the instance isn't in use.
     */
    public void setWorld(@Nullable BlockGetter world) {
        this.world = world;
    }

    /**
     * Sets the ray traced by {@link #trace()}, or by an iterator made from this context.
     */
    public ReusableRaycastContext setRay(Vec3 start, Vec3 end) {
        this.from = start;
        this.to = end;
        return this;
    }

    public BlockHitResult trace(Vec3 start, Vec3 end) {
        this.setStart(start);
        this.setEnd(end);
        return this.world.clip(this);
    }

    /**
     * Perform trace based on current values of start and end.
     */
    BlockHitResult trace() {
        return this.world.clip(this);
    }

    public Vec3 getStart() {
        return this.from;
    }

    void setStart(Vec3 point) {
        this.from = point;
    }

    public Vec3 getEnd() {
        return this.to;
    }

    void setEnd(Vec3 point) {
        this.to = point;
    }
}
