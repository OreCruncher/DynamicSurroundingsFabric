package org.orecruncher.dsurround.config.libraries.impl;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.data.DimensionConfig;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;

import java.util.*;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public final class DimensionLibrary implements IDimensionLibrary {

    private static final String FILE_NAME = "dimensions.json";
    private static final Codec<List<DimensionConfig>> CODEC = Codec.list(DimensionConfig.CODEC);

    private final IModLog logger;
    private final IMinecraftDirectories directories;
    private final ObjectArray<DimensionConfig> cache = new ObjectArray<>();
    private final Map<RegistryKey<World>, DimensionInfo> configs = new HashMap<>();
    private int version = 0;

    public DimensionLibrary(IModLog logger, IMinecraftDirectories directories) {
        this.logger = logger;
        this.directories = directories;
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {
        this.configs.clear();
        this.cache.clear();
        final Collection<IResourceAccessor> accessors = ResourceUtils.findConfigs(this.directories.getModDataDirectory().toFile(), FILE_NAME);

        IResourceAccessor.process(accessors, accessor -> {
            var cfg = accessor.as(CODEC);
            if (cfg != null)
                initFromConfig(cfg);
        });

        this.version++;

        this.logger.info("%d dimension configs loaded; version is now %d", this.configs.size(), this.version);
    }

    private void initFromConfig(final List<DimensionConfig> cfg) {
        cfg.forEach(this::register);
    }

    private DimensionConfig getData(final DimensionConfig entry) {
        final Optional<DimensionConfig> result = this.cache.stream().filter(e -> e.equals(entry)).findFirst();
        if (result.isPresent())
            return result.get();
        this.cache.add(entry);
        return entry;
    }

    private void register(final DimensionConfig entry) {
        if (entry.dimensionId != null) {
            final DimensionConfig data = getData(entry);
            if (data == entry)
                return;
            if (entry.cloudHeight.isPresent())
                data.cloudHeight = entry.cloudHeight;
            if (entry.seaLevel.isPresent())
                data.seaLevel = entry.seaLevel;
            if (entry.skyHeight.isPresent())
                data.skyHeight = entry.skyHeight;
            if (entry.alwaysOutside.isPresent())
                data.alwaysOutside = entry.alwaysOutside;
            if (entry.playBiomeSounds.isPresent())
                data.playBiomeSounds = entry.playBiomeSounds;
        }
    }

    @Override
    public DimensionInfo getData(final World world) {
        RegistryKey<World> key = world.getRegistryKey();
        DimensionInfo dimInfo = this.configs.get(key);

        if (dimInfo == null) {
            DimensionConfig config = null;
            Identifier location = key.getValue();
            for (final DimensionConfig e : this.cache)
                if (e.dimensionId.equals(location)) {
                    config = e;
                    break;
                }

            this.configs.put(key, dimInfo = new DimensionInfo(world, config));
        }
        return dimInfo;
    }

    @Override
    public Stream<String> dump() {
        return this.cache.stream().map(Object::toString).sorted();
    }
}