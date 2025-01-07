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
        return !this.rules.isEmpty() && !this.rules.getFirst().isDefaultRule();
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
            var mapped = Mapping.of(rule);

            // Find the first applicable rule that matches the factory that is needed.
            // It's possible to have multiple rules with the same factory. It can occur when
            // merging two different rule definitions where one has the factory as a default
            // and the other has block matchers.
            var existingRule = this.rules.stream().filter(r -> r.factory().equals(rule.factory())).findFirst();
            if (existingRule.isPresent()) {
                // If it is a default rule, we do not want to merge. Instead, we
                // insert prior. We need to preserve the existing rule as default.
                if (existingRule.get().isDefaultRule()) {
                    this.insertBeforeDefaultRule(mapped);
                } else {
                    // Need to add block matcher definitions
                    existingRule.get().merge(mapped);
                }
            } else {
                // Need to add the rule. If the last rule in the collection is all matches, we need
                // to insert prior. Otherwise, we append.
                var last = this.rules.getLast();
                if (last.isDefaultRule()) {
                    this.insertBeforeDefaultRule(mapped);
                } else {
                    this.rules.add(mapped);
                }
            }
        }
    }

    private void insertBeforeDefaultRule(Mapping mapping) {
        var last = this.rules.getLast();
        if (!last.isDefaultRule())
            throw new RuntimeException("Last rule in sound mapping configuration is not default");
        this.rules.remove(last);
        this.rules.add(mapping);
        this.rules.add(last);
    }

    public record Mapping(ObjectArray<IMatcher<BlockState>> blocks, ResourceLocation factory) {

        public static Mapping of(SoundMappingConfigRule.MappingRule mappingRule) {
            ObjectArray<IMatcher<BlockState>> blocks = new ObjectArray<>(mappingRule.blocks().size());
            blocks.addAll(mappingRule.blocks());
            return new Mapping(blocks, mappingRule.factory());
        }

        public Optional<ResourceLocation> findMatch(@Nullable BlockState state) {
            if (this.isDefaultRule())
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

        public boolean isDefaultRule() {
            return this.blocks.isEmpty();
        }

        public void merge(Mapping rule) {
            if (!this.factory.equals(rule.factory()))
                throw new RuntimeException("Unable to add mapping rule - factories do not match");
            this.blocks.addAll(rule.blocks());
        }
    }
}
