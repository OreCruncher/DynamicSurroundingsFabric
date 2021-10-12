package org.orecruncher.dsurround.mixins.core;

import net.minecraft.tag.TagGroup;
import net.minecraft.tag.TagManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TagManager.class)
public interface MixinTagManager {

    @Accessor("tagGroups")
    Map<RegistryKey<? extends Registry<?>>, TagGroup<?>> getTagGroups();
}
