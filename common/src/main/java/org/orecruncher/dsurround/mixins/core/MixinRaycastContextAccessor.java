package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClipContext.class)
public interface MixinRaycastContextAccessor {

    @Accessor("from")
    Vec3 dsurround$getStartPoint();

    @Accessor("from")
    @Mutable
    void dsurround$setStartPoint(Vec3 point);

    @Accessor("to")
    Vec3 dsurround$getEndPoint();

    @Accessor("to")
    @Mutable
    void dsurround$setEndPoint(Vec3 point);

    @Accessor("collisionContext")
    @Mutable
    void dsurround$setShapeContext(CollisionContext context);
}
