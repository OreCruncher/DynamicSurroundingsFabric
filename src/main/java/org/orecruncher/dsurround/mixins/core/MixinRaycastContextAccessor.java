package org.orecruncher.dsurround.mixins.core;

import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RaycastContext.class)
public interface MixinRaycastContextAccessor {

    @Accessor("start")
    Vec3d dsurround_getStartPoint();

    @Accessor("start")
    @Mutable
    void dsurround_setStartPoint(Vec3d point);

    @Accessor("end")
    Vec3d dsurround_getEndPoint();

    @Accessor("end")
    @Mutable
    void dsurround_setEndPoint(Vec3d point);

    @Accessor("shapeContext")
    @Mutable
    void dsurround_setShapeContext(ShapeContext context);
}
