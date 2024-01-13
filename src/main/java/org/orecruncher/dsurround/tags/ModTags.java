package org.orecruncher.dsurround.tags;

import net.minecraft.tags.TagKey;

import java.util.Collection;
import java.util.HashSet;

/**
 * Helps facilitate processing of tags during startup
 */
public final class ModTags {
    private ModTags() {

    }

    private static final Collection<TagKey<?>> MOD_TAGS = new HashSet<>();

    static {
        MOD_TAGS.addAll(BiomeTags.TAGS);
        MOD_TAGS.addAll(BlockEffectTags.TAGS);
        MOD_TAGS.addAll(EntityEffectTags.TAGS);
        MOD_TAGS.addAll(ItemEffectTags.TAGS);
        MOD_TAGS.addAll(ItemTags.TAGS);
        MOD_TAGS.addAll(OcclusionTags.TAGS);
        MOD_TAGS.addAll(ReflectanceTags.TAGS);
    }

    public static Collection<TagKey<?>> getModTags() {
        return MOD_TAGS;
    }
}
