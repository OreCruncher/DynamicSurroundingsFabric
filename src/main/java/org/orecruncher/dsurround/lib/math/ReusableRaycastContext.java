package org.orecruncher.dsurround.lib.math;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.mixins.core.MixinRaycastContextAccessor;

public class ReusableRaycastContext extends RaycastContext {

    private final World world;
    private final MixinRaycastContextAccessor accessor;

    public ReusableRaycastContext(World world, ShapeType shapeType, FluidHandling fluidHandling) {
        this(world, Vec3d.ZERO, Vec3d.ZERO, shapeType, fluidHandling);
    }

    public ReusableRaycastContext(World world, Vec3d start, Vec3d end, ShapeType shapeType, FluidHandling fluidHandling) {
        this(world, start, end, shapeType, fluidHandling, GameUtils.getPlayer().orElseThrow());

        // Override the shape context that was passed into the ctor
        this.accessor.dsurround_setShapeContext(ShapeContext.absent());
    }

    public ReusableRaycastContext(World world, Vec3d start, Vec3d end, ShapeType shapeType, FluidHandling fluidHandling, Entity entity) {
        super(start, end, shapeType, fluidHandling, entity);

        this.world = world;
        this.accessor = ((MixinRaycastContextAccessor)this);
    }

    public BlockHitResult trace(Vec3d start, Vec3d end) {
        this.setStart(start);
        this.setEnd(end);
        return this.world.raycast(this);
    }

    /**
     * Perform trace based on current values of start and end.
     */
    BlockHitResult trace() {
        return this.world.raycast(this);
    }

    public Vec3d getStart() {
        return this.accessor.dsurround_getStartPoint();
    }

    void setStart(Vec3d point) {
        this.accessor.dsurround_setStartPoint(point);
    }

    public Vec3d getEnd() {
        return this.accessor.dsurround_getEndPoint();
    }

    void setEnd(Vec3d point) {
        this.accessor.dsurround_setEndPoint(point);
    }
}
