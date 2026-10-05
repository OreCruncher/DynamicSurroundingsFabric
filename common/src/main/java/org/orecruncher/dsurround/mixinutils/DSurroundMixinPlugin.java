package org.orecruncher.dsurround.mixinutils;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Skips the mixins for mods that are optional, when the mod isn't installed. Otherwise Mixin reports their missing
 * target class as an error on every launch, which is harmless but alarming.
 * <p>
 * Whether the target is there is asked of Mixin's own class lookup, which reads the class without loading it, so
 * this works the same on Fabric and NeoForge. (This class can't be in the mixin package: Mixin doesn't allow
 * ordinary classes there.)
 */
public final class DSurroundMixinPlugin implements IMixinConfigPlugin {

    /**
     * Mixins whose target belongs to an optional mod: applied only when the target is present.
     */
    static final Map<String, String> OPTIONAL = Map.of(
            "org.orecruncher.dsurround.mixins.core.MixinClothAbstractConfigEntry", "me.shedaniel.clothconfig2.api.AbstractConfigEntry");

    /**
     * Whether to apply a mixin.
     *
     * @param isClassPresent whether a class (by its dotted name) is available
     */
    static boolean shouldApply(String mixinClassName, Predicate<String> isClassPresent) {
        var target = OPTIONAL.get(mixinClassName);
        return target == null || isClassPresent.test(target);
    }

    static boolean isClassPresent(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className.replace('.', '/'));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return shouldApply(mixinClassName, DSurroundMixinPlugin::isClassPresent);
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
