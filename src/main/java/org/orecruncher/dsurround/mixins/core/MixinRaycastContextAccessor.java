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
    Vec3d getStart();

    @Accessor("start")
    @Mutable
    void setStart(Vec3d point);

    @Accessor("end")
    Vec3d getEnd();

    @Accessor("end")
    @Mutable
    void setEnd(Vec3d point);

    @Accessor("entityPosition")
    @Mutable
    void setShapeContext(ShapeContext context);
}
