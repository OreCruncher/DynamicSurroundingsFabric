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
    Vec3d getStartPoint();

    @Accessor("start")
    @Mutable
    void setStartPoint(Vec3d point);

    @Accessor("end")
    Vec3d getEndPoint();

    @Accessor("end")
    @Mutable
    void setEndPoint(Vec3d point);

    @Accessor("entityPosition")
    @Mutable
    void setShapeContext(ShapeContext context);
}
