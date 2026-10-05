package org.orecruncher.dsurround.config.block;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.OcclusionTags;
import org.orecruncher.dsurround.tags.ReflectanceTags;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BlockInfoTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // The settings BlockInfo uses, by value
    private static final float REFLECTANCE_DEFAULT = 0.35F;
    private static final float OCCLUSION_DEFAULT = 0.5F;

    /**
     * A tag library holding just the block tags a test gives it.
     */
    private static final class Tags {
        final Map<TagKey<Block>, Set<Block>> members = new HashMap<>();

        Tags with(TagKey<Block> tag, Block... blocks) {
            this.members.computeIfAbsent(tag, t -> new HashSet<>()).addAll(List.of(blocks));
            return this;
        }

        ITagLibrary library() {
            return Fakes.of(ITagLibrary.class, Map.of("is", args ->
                    args[1] instanceof BlockState state
                            && this.members.getOrDefault(args[0], Set.of()).contains(state.getBlock())));
        }
    }

    /**
     * A condition evaluator that gives {@code chance} for any script, and counts how often it is asked.
     */
    private static final class Conditions {
        double chance;
        int evaluations;

        IConditionEvaluator evaluator() {
            return Fakes.of(IConditionEvaluator.class, Map.of(
                    "eval", args -> {
                        this.evaluations++;
                        return this.chance;
                    },
                    "check", args -> {
                        this.evaluations++;
                        return true;
                    }));
        }
    }

    private static ISoundFactory soundFactory(ResourceLocation location) {
        return Fakes.of(ISoundFactory.class, Map.of("getLocation", args -> location));
    }

    private static final ISoundLibrary SOUNDS = Fakes.of(ISoundLibrary.class,
            Map.of("getSoundFactoryOrDefault", args -> soundFactory((ResourceLocation) args[0])));

    private static ConfigServices services(Tags tags, Conditions conditions, RecordingLog log) {
        return new ConfigServices(log, SOUNDS, tags.library(), conditions.evaluator());
    }

    private static ConfigServices services(Tags tags) {
        return services(tags, new Conditions(), new RecordingLog());
    }

    private static BlockInfo info(Block block, Tags tags) {
        return new BlockInfo(1, block.defaultBlockState(), services(tags));
    }

    private static BlockConfigRule soundRule(Optional<Script> chance, String... sounds) {
        var acoustics = new java.util.ArrayList<AcousticConfig>();
        for (var s : sounds)
            acoustics.add(new AcousticConfig(ResourceLocation.fromNamespaceAndPath("test", s), Script.TRUE, WeightValue.of(10), SoundEventType.ADDITION));
        return new BlockConfigRule(List.of(), false, chance, acoustics, List.of());
    }

    // ---- Acoustics from tags

    @Test
    void modReflectanceAndOcclusionTagsSetTheValues() {
        var tags = new Tags().with(ReflectanceTags.HIGH, Blocks.DIRT).with(OcclusionTags.VERY_LOW, Blocks.DIRT);
        var info = info(Blocks.DIRT, tags);
        assertEquals(0.65F, info.getSoundReflectivity());
        assertEquals(0.15F, info.getSoundOcclusion());
    }

    @Test
    void modTagsWinOverEstimates() {
        // Leaves are estimated as barely reflective; a mod tag says otherwise
        var tags = new Tags().with(BlockTags.LEAVES, Blocks.OAK_LEAVES).with(ReflectanceTags.MAX, Blocks.OAK_LEAVES);
        assertEquals(1.0F, info(Blocks.OAK_LEAVES, tags).getSoundReflectivity());
    }

    @Test
    void estimatesFromVanillaTags() {
        var leaves = info(Blocks.OAK_LEAVES, new Tags().with(BlockTags.LEAVES, Blocks.OAK_LEAVES));
        assertEquals(0.15F, leaves.getSoundReflectivity());

        // Stone-like blocks reflect fully (this used an occlusion constant by mistake; same value)
        var stone = info(Blocks.STONE, new Tags().with(BlockTags.STONE_ORE_REPLACEABLES, Blocks.STONE));
        assertEquals(1.0F, stone.getSoundReflectivity());
        assertEquals(0.65F, stone.getSoundOcclusion());
    }

    @Test
    void estimatesFromTheNameWhenNoTagSays() {
        var glass = info(Blocks.GLASS, new Tags());
        assertEquals(0.5F, glass.getSoundReflectivity());
        assertEquals(0.35F, glass.getSoundOcclusion());
    }

    @Test
    void anOrdinaryBlockGetsTheDefaults() {
        var dirt = info(Blocks.DIRT, new Tags());
        assertEquals(REFLECTANCE_DEFAULT, dirt.getSoundReflectivity());
        assertEquals(OCCLUSION_DEFAULT, dirt.getSoundOcclusion());
        assertTrue(dirt.isDefault(), "a plain block should be able to share the default info");
    }

    @Test
    void aBlockYouCanSeeThroughBlocksLessSound() {
        // A door doesn't occlude in Minecraft's sense, and with no tag or telling name: translucent occlusion, so it
        // can't share the default info
        var info = info(Blocks.OAK_DOOR, new Tags());
        assertEquals(0.15F, info.getSoundOcclusion());
        assertFalse(info.isDefault());
    }

    // ---- Sounds

    @Test
    void noSoundsMeansNoSoundAndNoScriptRun() {
        var conditions = new Conditions();
        var info = new BlockInfo(1, Blocks.DIRT.defaultBlockState(), services(new Tags(), conditions, new RecordingLog()));
        assertTrue(info.getSoundToPlay(Randomizer.create(1)).isEmpty());
        assertEquals(0, conditions.evaluations, "the chance script ran for a block with no sounds");
    }

    @Test
    void aSoundIsChosenWhenTheChanceComesUp() {
        var conditions = new Conditions();
        var info = new BlockInfo(1, Blocks.DIRT.defaultBlockState(), services(new Tags(), conditions, new RecordingLog()));
        info.update(soundRule(Optional.of(new Script("1")), "a"));
        assertTrue(info.hasSoundsOrEffects());
        assertFalse(info.isDefault());

        conditions.chance = 1.0;
        var sound = info.getSoundToPlay(Randomizer.create(1));
        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "a"), sound.orElseThrow().getLocation());

        conditions.chance = 0.0;
        assertTrue(info.getSoundToPlay(Randomizer.create(1)).isEmpty());
    }

    @Test
    void duplicateSoundsAreReportedToTheLibrarysLog() {
        var log = new RecordingLog();
        var info = new BlockInfo(1, Blocks.DIRT.defaultBlockState(), services(new Tags(), new Conditions(), log));
        info.update(soundRule(Optional.empty(), "a"));
        info.update(soundRule(Optional.empty(), "a"));
        assertEquals(1, log.at(IModLog.Level.WARN).size());
        assertTrue(log.at(IModLog.Level.WARN).getFirst().message().contains("Duplicate acoustic entry"));
    }

    @Test
    void clearingSoundsRemovesEarlierOnes() {
        var info = new BlockInfo(1, Blocks.DIRT.defaultBlockState(), services(new Tags()));
        info.update(soundRule(Optional.empty(), "a"));
        info.update(new BlockConfigRule(List.of(), true, Optional.empty(), List.of(), List.of()));
        assertFalse(info.hasSoundsOrEffects());
    }
}
