package org.orecruncher.dsurround.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.State;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.lwjgl.system.CallbackI;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.block.BlockInfo;
import org.orecruncher.dsurround.config.data.BlockConfig;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.block.BlockStateMatcher;
import org.orecruncher.dsurround.lib.block.BlockStateMatcherMap;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.lib.validation.ListValidator;
import org.orecruncher.dsurround.lib.validation.Validators;
import org.orecruncher.dsurround.xface.IBlockStateExtended;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class BlockLibrary {

    private static final IModLog LOGGER = Client.LOGGER.createChild(BlockLibrary.class);
    private static final Type blockType = TypeToken.getParameterized(List.class, BlockConfig.class).getType();
    private static final String TAG_SPECIFIER = "#";
    private static final int INDEFINITE = -1;
    private static final BlockInfo DEFAULT = new BlockInfo(INDEFINITE);

    private static final BlockStateMatcherMap<BlockInfo> registry = new BlockStateMatcherMap<>();
    private static int version = 0;

    static {
        Validators.registerValidator(blockType, new ListValidator<BlockConfig>());
    }

    public static void load() {

        registry.clear();
        final Collection<IResourceAccessor> configs = ResourceUtils.findConfigs(Client.ModId, Client.DATA_PATH.toFile(), "blocks.json");
        IResourceAccessor.process(configs, accessor -> initFromConfig(accessor.as(blockType)));
        registry.values().forEach(BlockInfo::trim);
        version++;

        LOGGER.info("%d block configs loaded; version is now %d", registry.size(), version);
    }

    static void initFromConfig(final List<BlockConfig> config) {
        config.forEach(BlockLibrary::register);
    }

    private static BlockInfo getOrCreateBlockInfo(final BlockStateMatcher info) {
        return registry.computeIfAbsent(info, x -> new BlockInfo(version));
    }

    private static void register(final BlockConfig entry) {
        if (entry.blocks.isEmpty())
            return;

        for (final String blockName : entry.blocks) {
            final Collection<BlockStateMatcher> list = expand(blockName);

            for (final BlockStateMatcher blockInfo : list) {
                final BlockInfo blockData = getOrCreateBlockInfo(blockInfo);
                blockData.update(entry);
            }
        }
    }

    private static Collection<BlockStateMatcher> expand(final String blockName) {
        if (blockName.startsWith(TAG_SPECIFIER)) {
            final String tagName = blockName.substring(1);
            if (!Identifier.isValid(tagName)) {
                LOGGER.warn("Block name tag specification is invalid: %s", blockName);
                return ImmutableList.of();
            }

            Identifier tagId = new Identifier(tagName);

            try {
                assert GameUtils.getWorld() != null;
                var tag = GameUtils.getWorld().getTagManager()
                        .getTag(Registry.BLOCK_KEY, tagId, id -> new RuntimeException("Tag not found in registry"));
                if (tag != null) {
                    return tag.values().stream().map(BlockStateMatcher::create).filter(m -> !m.isEmpty()).collect(Collectors.toList());
                }
            } catch (Throwable t) {
                LOGGER.error(t, "Tag: %s", tagName);
            }
            LOGGER.debug("Unknown block tag '%s' in Block specification", tagName);
        } else {
            final BlockStateMatcher matcher = BlockStateMatcher.create(blockName);
            if (!matcher.isEmpty()) {
                return ImmutableList.of(matcher);
            }
            LOGGER.debug("Unknown block name '%s' in Block Specification", blockName);
        }

        return ImmutableList.of();
    }

    public static BlockInfo getBlockInfo(BlockState state) {
        var info = ((IBlockStateExtended) state).getBlockInfo();
        if (info != null) {
            if (info.getVersion() == version || info == DEFAULT)
                return info;
        }

        info = registry.get(state);
        if (info == null)
            info = DEFAULT;
        ((IBlockStateExtended) state).setBlockInfo(info);

        return info;
    }

    public static Stream<String> dumpBlockStates() {
        return GameUtils.getRegistryManager().get(Registry.BLOCK_KEY).stream()
                .flatMap(block -> block.getStateManager().getStates().stream())
                .map(State::toString)
                .sorted();
    }

    public static Stream<String> dumpBlockInfo() {
        return registry.entrySet().stream()
                .map(kvp -> kvp.getKey().toString() + "\n" + kvp.getValue().toString() + "\n")
                .sorted();
    }

    public static Stream<String> dumpBlocks() {
        var blockRegistry = GameUtils.getRegistryManager().get(Registry.BLOCK_KEY).getEntries();
        return blockRegistry.stream()
                .map(kvp -> formatBlockOutput(kvp.getKey().getValue(), kvp.getValue()))
                .sorted();
    }

    public static Stream<String> dumpBlocksByTag() {
        var tagGroup = GameUtils.getTagGroup(Registry.BLOCK_KEY);
        if (tagGroup != null) {
            return tagGroup.getTags().entrySet()
                    .stream()
                    .filter(kvp -> kvp.getValue().values().size() > 0)
                    .map(BlockLibrary::formatBlockTagOutput)
                    .sorted();
        }

        return Stream.empty();
    }

    private static String formatBlockTagOutput(Map.Entry<Identifier, Tag<Block>> kvp) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tag: ").append(kvp.getKey());
        for (var e : kvp.getValue().values())
            builder.append("\n    ").append(e.toString());
        builder.append("\n");
        return builder.toString();
    }

    private static String formatBlockOutput(Identifier id, Block block) {
        var tags = GameUtils.getWorld().getTagManager()
                .getOrCreateTagGroup(Registry.BLOCK_KEY)
                .getTagsFor(block).stream()
                .map(Identifier::toString)
                .sorted()
                .collect(Collectors.joining(","));

        StringBuilder builder = new StringBuilder();
        builder.append(id.toString());
        builder.append("\nTags: ").append(tags);
        builder.append("\nstates [\n");
        for (var blockState : block.getStateManager().getStates()) {
            builder.append(blockState.toString()).append("\n");
            var info = getBlockInfo(blockState);
            if (info != DEFAULT)
                builder.append(info.toString());
        }
        builder.append("]\n");
        return builder.toString();
    }

}
