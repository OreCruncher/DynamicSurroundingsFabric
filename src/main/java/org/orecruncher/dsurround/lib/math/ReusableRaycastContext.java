package org.orecruncher.dsurround.lib.math;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.mixins.core.MixinRaycastContextAccessor;

public class ReusableRaycastContext extends ClipContext {

    private final Level world;
    private final MixinRaycastContextAccessor accessor;

    public ReusableRaycastContext(Level world, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling) {
        this(world, Vec3.ZERO, Vec3.ZERO, shapeType, fluidHandling);
    }

    public ReusableRaycastContext(Level world, Vec3 start, Vec3 end, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling) {
        this(world, start, end, shapeType, fluidHandling, GameUtils.getPlayer().orElseThrow());

        // Override the shape context that was passed into the ctor
        this.accessor.dsurround_setShapeContext(CollisionContext.empty());
    }

    public ReusableRaycastContext(Level world, Vec3 start, Vec3 end, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, Entity entity) {
        super(start, end, shapeType, fluidHandling, entity);

        this.world = world;
        this.accessor = ((MixinRaycastContextAccessor)this);
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
        return this.accessor.dsurround_getStartPoint();
    }

    void setStart(Vec3 point) {
        this.accessor.dsurround_setStartPoint(point);
    }

    public Vec3 getEnd() {
        return this.accessor.dsurround_getEndPoint();
    }

    void setEnd(Vec3 point) {
        this.accessor.dsurround_setEndPoint(point);
    }
}
