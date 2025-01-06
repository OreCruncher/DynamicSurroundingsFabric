package org.orecruncher.dsurround.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.data.SoundMappingConfigRule;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.util.Optional;

public record SoundMapping(ResourceLocation soundEvent, ObjectArray<Mapping> rules) {

    public static SoundMapping of(SoundMappingConfigRule rule) {
        ObjectArray<Mapping> mappings = new ObjectArray<>(rule.rules().size());
        rule.rules().forEach(r -> mappings.add(Mapping.of(r)));
        return new SoundMapping(rule.soundEvent(), mappings);
    }

    public boolean isBlockStateNeeded() {
        return !this.rules.isEmpty() && !this.rules.getFirst().blocks.isEmpty();
    }

    public Optional<ResourceLocation> findMatch(@Nullable BlockState state) {
        Optional<ResourceLocation> factory = Optional.empty();
        for (var rule : this.rules) {
            factory = rule.findMatch(state);
            if (factory.isPresent())
                break;
        }
        return factory;
    }

    public void merge(SoundMappingConfigRule mapping) {
        if (!this.soundEvent.equals(mapping.soundEvent()))
            throw new RuntimeException("Unable to merge sound mapping rule - factories do not match");

        for (var rule : mapping.rules()) {
            var existingRule = this.rules.stream().filter(r -> r.factory().equals(rule.factory())).findFirst();
            if (existingRule.isPresent()) {
                // Need to add block matcher definitions
                existingRule.get().merge(rule);
            } else {
                // Need to add the rule. If the last rule in the collection is all matches, we need
                // to insert prior. Otherwise, we can append.
                var last = this.rules.getLast();
                if (last.blocks().isEmpty()) {
                    this.rules.remove(last);
                    this.rules.add(Mapping.of(rule));
                    this.rules.add(last);
                } else {
                    rules.add(Mapping.of(rule));
                }
            }
        }
    }

    public record Mapping(ObjectArray<IMatcher<BlockState>> blocks, ResourceLocation factory) {

        public static Mapping of(SoundMappingConfigRule.MappingRule mappingRule) {
            ObjectArray<IMatcher<BlockState>> blocks = new ObjectArray<>(mappingRule.blocks().size());
            blocks.addAll(mappingRule.blocks());
            return new Mapping(blocks, mappingRule.factory());
        }

        public Optional<ResourceLocation> findMatch(@Nullable BlockState state) {
            if (this.blocks.isEmpty())
                return Optional.of(this.factory);
            // Since the rules have BlockState matching if a null state is provided
            // return empty - nothing could be matched.
            if (state == null)
                return Optional.empty();
            for( var rule : this.blocks) {
                if (rule.match(state))
                    return Optional.of(this.factory);
            }
            return Optional.empty();
        }

        public void merge(SoundMappingConfigRule.MappingRule rule) {
            if (!this.factory.equals(rule.factory()))
                throw new RuntimeException("Unable to add mapping rule - factories do not match");
            this.blocks.addAll(rule.blocks());
        }
    }
}
