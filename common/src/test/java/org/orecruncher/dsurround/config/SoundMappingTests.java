package org.orecruncher.dsurround.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.data.SoundMappingConfigRule;
import org.orecruncher.dsurround.config.data.SoundMappingConfigRule.MappingRule;
import org.orecruncher.dsurround.lib.codec.IMatcher;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for merging sound mapping rules: the order is significant, and the default rule (no block matchers) must
 * stay last.
 */
public class SoundMappingTests {

    private static final ResourceLocation SOUND = ResourceLocation.parse("minecraft:block.stone.step");

    private static ResourceLocation factory(String name) {
        return ResourceLocation.parse("dsurround:" + name);
    }

    private static List<IMatcher<BlockState>> blocks(int count) {
        var list = new ArrayList<IMatcher<BlockState>>();
        for (int i = 0; i < count; i++)
            list.add(state -> false);
        return list;
    }

    private static List<ResourceLocation> factories(SoundMapping mapping) {
        var list = new ArrayList<ResourceLocation>();
        mapping.rules().forEach(r -> list.add(r.factory()));
        return list;
    }

    @Test
    void newRuleIsInsertedBeforeTheDefault() {
        var mapping = SoundMapping.of(new SoundMappingConfigRule(SOUND, List.of(
                new MappingRule(blocks(1), factory("a")),
                new MappingRule(blocks(1), factory("b")),
                new MappingRule(List.of(), factory("default")))));

        mapping.merge(new SoundMappingConfigRule(SOUND, List.of(new MappingRule(blocks(1), factory("c")))));

        assertEquals(List.of(factory("a"), factory("b"), factory("c"), factory("default")), factories(mapping));
        assertTrue(mapping.rules().getLast().isDefaultRule());
    }

    @Test
    void severalMergesKeepTheOrderAndTheDefaultLast() {
        // Guards the order. The old remove(last) found the default by equality and filled the gap from the end; it
        // only worked because Mapping equality is effectively identity. removeLast doesn't depend on that.
        var mapping = SoundMapping.of(new SoundMappingConfigRule(SOUND, List.of(
                new MappingRule(blocks(1), factory("a")),
                new MappingRule(List.of(), factory("default")))));

        for (var name : List.of("b", "c", "d"))
            mapping.merge(new SoundMappingConfigRule(SOUND, List.of(new MappingRule(blocks(2), factory(name)))));

        assertEquals(List.of(factory("a"), factory("b"), factory("c"), factory("d"), factory("default")), factories(mapping));
    }

    @Test
    void ruleForTheDefaultsFactoryIsInsertedBeforeIt() {
        // Matchers for the default's own factory become a separate rule before the default
        var mapping = SoundMapping.of(new SoundMappingConfigRule(SOUND, List.of(
                new MappingRule(blocks(1), factory("a")),
                new MappingRule(List.of(), factory("default")))));

        mapping.merge(new SoundMappingConfigRule(SOUND, List.of(new MappingRule(blocks(1), factory("default")))));

        assertEquals(List.of(factory("a"), factory("default"), factory("default")), factories(mapping));
        assertFalse(mapping.rules().get(1).isDefaultRule());
        assertTrue(mapping.rules().getLast().isDefaultRule());
    }

    @Test
    void existingRuleGainsTheNewMatchers() {
        var mapping = SoundMapping.of(new SoundMappingConfigRule(SOUND, List.of(
                new MappingRule(blocks(1), factory("a")),
                new MappingRule(List.of(), factory("default")))));

        mapping.merge(new SoundMappingConfigRule(SOUND, List.of(new MappingRule(blocks(2), factory("a")))));

        assertEquals(List.of(factory("a"), factory("default")), factories(mapping));
        assertEquals(3, mapping.rules().getFirst().blocks().size());
    }

    @Test
    void withoutADefaultNewRulesAreAppended() {
        var mapping = SoundMapping.of(new SoundMappingConfigRule(SOUND, List.of(new MappingRule(blocks(1), factory("a")))));

        mapping.merge(new SoundMappingConfigRule(SOUND, List.of(new MappingRule(blocks(1), factory("b")))));

        assertEquals(List.of(factory("a"), factory("b")), factories(mapping));
    }

    @Test
    void defaultRuleMatchesWhenNothingElseDoes() {
        var mapping = SoundMapping.of(new SoundMappingConfigRule(SOUND, List.of(
                new MappingRule(blocks(1), factory("a")),
                new MappingRule(List.of(), factory("default")))));

        // A null state can't match block rules, only the default
        assertEquals(factory("default"), mapping.findMatch(null).orElseThrow());
        assertTrue(mapping.isBlockStateNeeded());
    }
}
