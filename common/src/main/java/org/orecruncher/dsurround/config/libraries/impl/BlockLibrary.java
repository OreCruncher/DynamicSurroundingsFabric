package org.orecruncher.dsurround.config.libraries.impl;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.block.BlockInfo;
import org.orecruncher.dsurround.config.block.BlockInfoBuilder;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;

/**
 * Per block state information: acoustic properties (sound reflectivity and occlusion, used by the enhanced sound
 * processing) and the sounds and effects configured in blocks.json, as a {@link BlockInfo}.
 * <ul>
 *   <li>{@link #getBlockInfo} builds the info on first request and caches it. Client thread only.</li>
 *   <li>{@link #getBlockInfoWeak} returns whatever is cached, or the default info, without building anything. Safe from any
 *       thread; the sound processing threads use it.</li>
 *   <li>Blocks with no configuration and default acoustics share one default info instance, to save memory.</li>
 *   <li>Every reload (resources or tags) starts a fresh cache and seeds it with common terrain blocks, so sound
 *       processing has their real acoustics straight away.</li>
 * </ul>
 */
public class BlockLibrary implements IBlockLibrary {

    private static final String FILE_NAME = "blocks.json";
    private static final Codec<List<BlockConfigRule>> CODEC = Codec.list(BlockConfigRule.CODEC);

    private static final int INDEFINITE = -1;

    private final IModLog logger;
    private final ITagLibrary tagLibrary;
    // Handed to every BlockInfo built
    private final ConfigServices services;
    // Shared by every block with no configuration and default acoustics
    private final BlockInfo defaultInfo;

    /**
     * Cache of block info, indexed by block state id (Block.getId()).
     * <p>
     * Filled by the client thread in getBlockInfo() and read by the sound thread in getBlockInfoWeak(). The atomic
     * array gives each entry safe publication between the two; the field is volatile so that replacing the whole
     * array (to clear it on reload, or grow it) is seen by both.
     * <p>
     * State ids can be renumbered after the cache is filled (registry sync when joining a server), so each entry
     * records the state it belongs to, and a lookup only counts as a hit when that state matches.
     */
    private volatile AtomicReferenceArray<CacheEntry> blocks = newCache(0);

    private record CacheEntry(BlockState state, BlockInfo info) {
    }

    /*
     * Blocks that make up most terrain, cached as soon as the library reloads instead of waiting for them to be
     * looked up. Sound processing reads acoustics with getBlockInfoWeak(), which never builds anything, so an
     * uncached block gets the default reflectivity and occlusion. That's right for plain blocks, but wrong for blocks
     * like stone, which occlude more than the default. These are what sound rays hit most.
     *
     * Tags pick up modded variants. Glass uses vanilla's IMPERMEABLE (glass, stained and tinted glass) plus the
     * c:glass_blocks and c:glass_panes convention tags, which Fabric and NeoForge both provide; vanilla has no tag
     * for panes. A few common blocks aren't in any suitable tag, so they are listed directly.
     * Tags are only bound once in a world, so on the title screen only the listed blocks are seeded; the tag sync
     * when joining seeds the rest.
     */
    private static final List<TagKey<Block>> SEED_TAGS = List.of(
            BlockTags.BASE_STONE_OVERWORLD,
            BlockTags.BASE_STONE_NETHER,
            BlockTags.STONE_ORE_REPLACEABLES,
            BlockTags.DEEPSLATE_ORE_REPLACEABLES,
            BlockTags.DIRT,
            BlockTags.SAND,
            BlockTags.LOGS,
            BlockTags.LEAVES,
            BlockTags.ICE,
            BlockTags.TERRACOTTA,
            BlockTags.PLANKS,
            BlockTags.IMPERMEABLE,
            conventionTag("glass_blocks"),
            conventionTag("glass_panes"));

    private static final List<Block> SEED_BLOCKS = List.of(
            Blocks.GRAVEL,
            Blocks.CLAY,
            Blocks.SANDSTONE,
            Blocks.RED_SANDSTONE,
            Blocks.SNOW_BLOCK,
            Blocks.END_STONE,
            Blocks.OBSIDIAN,
            // Also covered by the tags above, but listed so they are seeded on the title screen too
            Blocks.GLASS,
            Blocks.GLASS_PANE);

    private static TagKey<Block> conventionTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private final Collection<BlockConfigRule> blockConfigs = new ObjectArray<>();
    // A broken rule fails for every block it is checked against, so each is reported once per reload
    private final LogThrottle<Object> ruleFailures;
    private int version = 0;

    public BlockLibrary(IModLog logger, ITagLibrary tagLibrary, ISoundLibrary soundLibrary, IConditionEvaluator conditionEvaluator) {
        this.logger = ModLog.createChild(logger, "BlockLibrary");
        this.tagLibrary = tagLibrary;
        this.services = new ConfigServices(this.logger, soundLibrary, tagLibrary, conditionEvaluator);
        this.defaultInfo = new BlockInfoBuilder(INDEFINITE, this.services).build();
        this.ruleFailures = LogThrottle.oncePerKey(this.logger, "block rule failures", "the next reload");
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {

        this.version++;
        this.ruleFailures.reset();

        if (scope == IReloadEvent.Scope.TAGS) {
            // Tags affect block info (acoustic properties, which configs match), so drop the cache to rebuild it
            // lazily. The version bump alone isn't enough: blocks stored as the shared default info are accepted whatever
            // their version, so they would never pick up the new tags.
            this.blocks = newCache(0);
            this.logger.info("received tag update notification; version is now %d", this.version);
            this.seedCache();
            return;
        }

        this.blocks = newCache(0);
        this.blockConfigs.clear();

        var findResults = resourceUtilities.findModResources(CODEC, FILE_NAME);
        findResults.forEach(result -> this.blockConfigs.addAll(result.resourceContent()));

        this.logger.info("%d block configs loaded; version is now %d", blockConfigs.size(), version);
        this.seedCache();
    }

    /**
     * Caches every state of the common terrain blocks (see SEED_TAGS and SEED_BLOCKS). Runs on the client thread as
     * part of a reload, after the tag library has reloaded, so it is the cache's only writer at the time and sees
     * current tags.
     */
    private void seedCache() {
        final long start = System.nanoTime();

        Set<Block> seeds = Collections.newSetFromMap(new IdentityHashMap<>());
        seeds.addAll(SEED_BLOCKS);
        for (var tag : SEED_TAGS) {
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag))
                seeds.add(holder.value());
        }

        // A block that fails is reported and skipped; the rest are still seeded
        int[] states = {0};
        RuleGuard.forEach(seeds,
                block -> {
                    for (var state : block.getStateDefinition().getPossibleStates()) {
                        this.getBlockInfo(state);
                        states[0]++;
                    }
                },
                (block, t) -> this.ruleFailures.error(block, t, "Unable to seed block info for %s", block));

        final long micros = (System.nanoTime() - start) / 1_000;
        this.logger.info("seeded %d block states from %d blocks in %d.%03d ms",
                states[0], seeds.size(), micros / 1_000, micros % 1_000);
    }

    /**
     * Safe from any thread. Returns whatever is cached, or the default info if nothing is, without building anything.
     */
    @Override
    public BlockInfo getBlockInfoWeak(BlockState state) {
        var entry = lookup(this.blocks, state);
        return entry != null ? entry.info() : this.defaultInfo;
    }

    /**
     * Client thread only: builds and caches the info if it isn't cached yet.
     * <p>
     * A cached entry is always current: every reload bumps the version and starts a new, empty cache, so there is
     * no need to compare versions here.
     */
    @Override
    public BlockInfo getBlockInfo(BlockState state) {
        var entry = lookup(this.blocks, state);
        if (entry != null)
            return entry.info();

        // OK - need to build out info for the block.
        final var builder = new BlockInfoBuilder(this.version, state, this.services);
        // A rule that throws is reported once and skipped, rather than failing this block's lookup every time
        RuleGuard.forEach(this.blockConfigs,
                rule -> {
                    if (rule.match(state))
                        builder.apply(rule);
                },
                (rule, t) -> this.ruleFailures.error(rule, t, "Unable to apply block rule to %s [%s]", state, rule));

        // Optimization to reduce memory bloat.  Coalesce blocks that do not have any special
        // processing to the default info.
        var info = builder.isDefault() ? this.defaultInfo : builder.build();

        this.store(state, info);
        return info;
    }

    /**
     * The cached entry for {@code state}, or null if there is none (or the slot belongs to a different state after
     * the ids were renumbered).
     */
    private static CacheEntry lookup(AtomicReferenceArray<CacheEntry> cache, BlockState state) {
        int id = Block.getId(state);
        if (id < 0 || id >= cache.length())
            return null;
        var entry = cache.get(id);
        return entry != null && entry.state() == state ? entry : null;
    }

    private void store(BlockState state, BlockInfo info) {
        int id = Block.getId(state);
        if (id < 0)
            return; // Not a registered state; nothing to index it by

        var cache = this.blocks;
        if (id >= cache.length()) {
            // More states than when the cache was made (registry sync, late registration). Grow, keeping entries.
            var grown = newCache(id + 1);
            for (int i = 0; i < cache.length(); i++)
                grown.set(i, cache.get(i));
            this.blocks = grown;
            cache = grown;
        }
        cache.set(id, new CacheEntry(state, info));
    }

    /**
     * An empty cache with room for every block state currently registered, and at least {@code minSize}.
     */
    private static AtomicReferenceArray<CacheEntry> newCache(int minSize) {
        return new AtomicReferenceArray<>(Math.max(minSize, Block.BLOCK_STATE_REGISTRY.size()));
    }

    @Override
    public Stream<String> dumpBlockStates() {
        return RegistryUtils.getRegistry(Registries.BLOCK).orElseThrow()
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
        var blockRegistry = RegistryUtils.getRegistry(Registries.BLOCK).orElseThrow();
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
        var blockRegistry = RegistryUtils.getRegistry(Registries.BLOCK).orElseThrow();

        StringBuilder builder = new StringBuilder();
        builder.append("Tag: ").append(blockTag.location());
        blocks.stream()
                .map(b -> Objects.requireNonNull(blockRegistry.getKey(b)).toString())
                .sorted()
                .forEach(tag -> builder.append("\n  ").append(tag));
        builder.append("\n");
        return builder.toString();
    }

    private String formatBlockOutput(ResourceLocation id, Block block, boolean noStates) {
        var entry = RegistryUtils.getRegistryEntry(Registries.BLOCK, block).orElseThrow();

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
                builder.append("  ").append(blockState.toString()).append("\n");
            }
            builder.append("]");
        }

        builder.append("\n");

        return builder.toString();
    }
}
