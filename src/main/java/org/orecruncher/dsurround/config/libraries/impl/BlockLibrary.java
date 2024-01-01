package org.orecruncher.dsurround.config.libraries.impl;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import org.orecruncher.dsurround.config.block.BlockInfo;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.mixinutils.IBlockStateExtended;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class BlockLibrary implements IBlockLibrary {

    private static final String FILE_NAME = "blocks.json";
    private static final Codec<List<BlockConfigRule>> CODEC = Codec.list(BlockConfigRule.CODEC);

    private static final int INDEFINITE = -1;

    private static final BlockInfo DEFAULT = new BlockInfo(INDEFINITE) {
        @Override
        public boolean isDefault() {
            return true;
        }
    };

    private final IModLog logger;
    private final IMinecraftDirectories directories;
    private final IConditionEvaluator conditionEvaluator;
    private final ITagLibrary tagLibrary;

    private final Collection<BlockConfigRule> blockConfigs = new ObjectArray<>();
    private int version = 0;

    public BlockLibrary(IModLog logger, IMinecraftDirectories directories, IConditionEvaluator conditionEvaluator, ITagLibrary tagLibrary) {
        this.logger = logger;
        this.directories = directories;
        this.conditionEvaluator = conditionEvaluator;
        this.tagLibrary = tagLibrary;
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {

        this.blockConfigs.clear();
        final Collection<IResourceAccessor> accessors = ResourceUtils.findConfigs(this.directories.getModDataDirectory().toFile(), FILE_NAME);

        IResourceAccessor.process(accessors, accessor -> {
            var cfg = accessor.as(CODEC);
            if (cfg != null)
                this.blockConfigs.addAll(cfg);
        });

        this.version++;

        this.logger.info("%d block configs loaded; version is now %d", blockConfigs.size(), version);
    }

    @Override
    public BlockInfo getBlockInfo(BlockState state) {
        var info = ((IBlockStateExtended) state).dsurround_getBlockInfo();
        if (info != null) {
            if (info.getVersion() == this.version || info == DEFAULT)
                return info;
        }

        // OK - need to build out info for the block.
        info = new BlockInfo(this.version, state, this.conditionEvaluator);
        this.blockConfigs.stream()
                .filter(c -> c.match(state))
                .forEach(info::update);

        // Optimization to reduce memory bloat.  Coalesce blocks that do not have any special
        // processing to the DEFAULT, and trim the others to release memory that is not needed.
        if (info.isDefault())
            info = DEFAULT;
        else
            info.trim();

        ((IBlockStateExtended) state).dsurround_setBlockInfo(info);

        return info;
    }

    @Override
    public Stream<String> dumpBlockStates() {
        return GameUtils.getRegistry(Registries.BLOCK).orElseThrow()
                .stream()
                .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                .map(StateHolder::toString)
                .sorted();
    }

    @Override
    public Stream<String> dumpBlockConfigRules() {
        return this.blockConfigs.stream().map(BlockLibrary::formatBlockConfigRuleOutput).sorted();
    }

    @Override
    public Stream<String> dumpBlocks(boolean noStates) {
        var blockRegistry = GameUtils.getRegistry(Registries.BLOCK).orElseThrow();
        var entrySet = blockRegistry.entrySet();
        return entrySet.stream().map(kvp -> formatBlockOutput(kvp.getKey().location(), kvp.getValue(), noStates)).sorted();
    }

    @Override
    public Stream<String> dump() {
        return this.tagLibrary.getEntriesByTag(Registries.BLOCK)
                .map(pair -> formatBlockTagOutput(pair.key(), pair.value()))
                .sorted();
    }

    private static String formatBlockConfigRuleOutput(BlockConfigRule rule) {
        return rule.toString();
    }

    private static String formatBlockTagOutput(TagKey<Block> blockTag, Set<Block> blocks) {
        var blockRegistry = GameUtils.getRegistry(Registries.BLOCK).orElseThrow();

        StringBuilder builder = new StringBuilder();
        builder.append("Tag: ").append(blockTag.location());
        blocks.stream()
                .map(blockRegistry::getResourceKey)
                .sorted()
                .forEach(tag -> builder.append("\n  ").append(tag));
        builder.append("\n");
        return builder.toString();
    }

    private String formatBlockOutput(ResourceLocation id, Block block, boolean noStates) {
        var entry = GameUtils.getRegistryEntry(Registries.BLOCK, block).orElseThrow();

        var t = this.tagLibrary.streamTags(entry);
        var tags = this.tagLibrary.asString(t);

        StringBuilder builder = new StringBuilder();
        builder.append(id.toString());
        builder.append("\nTags: ").append(tags);

        var info = getBlockInfo(block.defaultBlockState());
        builder.append("\nreflectance: ").append(info.getSoundReflectivity());
        builder.append("; occlusion: ").append(info.getSoundOcclusion());

        if (!noStates) {
            builder.append("\nstates [\n");
            for (var blockState : block.getStateDefinition().getPossibleStates()) {
                builder.append(blockState.toString()).append("\n");
                info = getBlockInfo(blockState);
                builder.append(info);
            }
            builder.append("]");
        }

        builder.append("\n");

        return builder.toString();
    }
}
