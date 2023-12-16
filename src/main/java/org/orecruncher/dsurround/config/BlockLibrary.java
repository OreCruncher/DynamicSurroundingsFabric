package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.State;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.block.BlockInfo;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.tags.TagHelpers;
import org.orecruncher.dsurround.xface.IBlockStateExtended;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class BlockLibrary {

    private static final String FILE_NAME = "blocks.json";
    private static final Codec<List<BlockConfigRule>> CODEC = Codec.list(BlockConfigRule.CODEC);
    private static final IModLog LOGGER = Client.LOGGER.createChild(BlockLibrary.class);

    private static final int INDEFINITE = -1;

    private static final BlockInfo DEFAULT = new BlockInfo(INDEFINITE) {
        @Override
        public boolean isDefault() {
            return true;
        }
    };

    private static final Collection<BlockConfigRule> blockConfigs = new ObjectArray<>();
    private static int version = 0;

    public static void load() {

        blockConfigs.clear();
        final Collection<IResourceAccessor> accessors = ResourceUtils.findConfigs(Client.DATA_PATH.toFile(), FILE_NAME);

        IResourceAccessor.process(accessors, accessor -> {
            var cfg = accessor.as(CODEC);
            if (cfg != null) blockConfigs.addAll(cfg);
        });

        version++;

        LOGGER.info("%d block configs loaded; version is now %d", blockConfigs.size(), version);
    }

    public static BlockInfo getBlockInfo(BlockState state) {
        var info = ((IBlockStateExtended) state).getBlockInfo();
        if (info != null) {
            if (info.getVersion() == version || info == DEFAULT) return info;
        }

        // OK - need to build out an info for the block.
        info = new BlockInfo(version, state);
        for (var cfg : blockConfigs) {
            if (cfg.match(state)) info.update(cfg);
        }

        // Optimization to reduce memory bloat.  Coalesce blocks that do not have any special
        // processing to the DEFAULT, and trim the others to release memory that is not needed.
        if (info.isDefault()) info = DEFAULT;
        else info.trim();

        ((IBlockStateExtended) state).setBlockInfo(info);

        return info;
    }

    public static Stream<String> dumpBlockStates() {
        return GameUtils.getRegistryManager().get(RegistryKeys.BLOCK).stream().flatMap(block -> block.getStateManager().getStates().stream()).map(State::toString).sorted();
    }

    public static Stream<String> dumpBlockConfigRules() {
        return blockConfigs.stream().map(BlockLibrary::formatBlockConfigRuleOutput).sorted();
    }

    public static Stream<String> dumpBlocks(boolean noStates) {
        var blockRegistry = GameUtils.getRegistryManager().get(RegistryKeys.BLOCK).getEntrySet();
        return blockRegistry.stream().map(kvp -> formatBlockOutput(kvp.getKey().getValue(), kvp.getValue(), noStates)).sorted();
    }

    public static Stream<String> dumpBlocksByTag() {
        var tagGroup = TagHelpers.getTagGroup(RegistryKeys.BLOCK);
        if (tagGroup != null) {
            return tagGroup.filter(pair -> pair.value().findAny().isPresent()).map(pair -> BlockLibrary.formatBlockTagOutput(pair.key(), pair.value())).sorted();
        }

        return Stream.empty();
    }

    private static String formatBlockConfigRuleOutput(BlockConfigRule rule) {
        return "";
    }

    private static String formatBlockTagOutput(Block block, Stream<TagKey<Block>> tags) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tag: ").append(block);
        tags.forEach(tag -> builder.append("\n    ").append(tag.toString()));
        builder.append("\n");
        return builder.toString();
    }

    private static String formatBlockOutput(Identifier id, Block block, boolean noStates) {
        var blocks = GameUtils.getWorld().getRegistryManager().get(RegistryKeys.BLOCK);

        var tags = "null";
        var entry = blocks.getEntry(blocks.getRawId(block));
        if (entry.isPresent()) {
            tags = TagHelpers.asString(entry.get().streamTags());
        }

        StringBuilder builder = new StringBuilder();
        builder.append(id.toString());
        builder.append("\nTags: ").append(tags);

        var info = getBlockInfo(block.getDefaultState());
        builder.append("\nreflectance: ").append(info.getSoundReflectivity());
        builder.append("; occlusion: ").append(info.getSoundOcclusion());

        if (!noStates) {
            builder.append("\nstates [\n");
            for (var blockState : block.getStateManager().getStates()) {
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
