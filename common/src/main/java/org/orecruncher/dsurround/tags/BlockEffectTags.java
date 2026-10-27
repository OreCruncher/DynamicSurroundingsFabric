package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class BlockEffectTags {

    static final Collection<TagKey<Block>> TAGS = new HashSet<>();

    @SuppressWarnings("unused")
    public static final TagKey<Block> FIREFLIES = of("fireflies");
    // Lily pads and the like, where frogs croak
    @SuppressWarnings("unused")
    public static final TagKey<Block> FROG_CROAKS = of("frog_croaks");
    public static final TagKey<Block> FLOOR_SQUEAKS = of("floor_squeaks");
    public static final TagKey<Block> BRUSH_STEP = of("brush_step");
    public static final TagKey<Block> LEAVES_STEP = of("leaves_step");
    public static final TagKey<Block> STRAW_STEP = of("straw_step");
    public static final TagKey<Block> WATERY_STEP = of("watery_step");
    public static final TagKey<Block> STEAM_PRODUCERS = of("steam_producers");
    public static final TagKey<Block> HEAT_PRODUCERS = of("heat_producers");
    // Skulls whose eyes burn with fire, and with blue soul fire
    public static final TagKey<Block> SKULL_FLAMES = of("skull_flames");
    public static final TagKey<Block> SOUL_SKULL_FLAMES = of("soul_skull_flames");
    public static final TagKey<Block> RAIN_ABSORBING = of("rain_absorbing");
    public static final TagKey<Block> DUST_SOURCES = of("dust_sources");
    public static final TagKey<Block> WINDOWS = of("windows");
    public static final TagKey<Block> FROSTED_WINDOWS = of("frosted_windows");
    // Blocks feet leave prints in, besides those that sound like sand, gravel, snow or mud underfoot
    public static final TagKey<Block> FOOTPRINTS = of("footprints");

    private static TagKey<Block> of(String id) {
        var tagKey = TagKey.create(Registries.BLOCK, Constants.asId("effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }
}
