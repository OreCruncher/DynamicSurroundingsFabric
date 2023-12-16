package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.DimensionConfig;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;

import java.util.*;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public final class DimensionLibrary {

    private static final String FILE_NAME = "dimensions.json";
    private static final Codec<List<DimensionConfig>> CODEC = Codec.list(DimensionConfig.CODEC);
    private static final ObjectArray<DimensionConfig> cache = new ObjectArray<>();
    private static final Map<RegistryKey<World>, DimensionInfo> configs = new HashMap<>();

    public static void load() {
        configs.clear();
        cache.clear();
        final Collection<IResourceAccessor> accessors = ResourceUtils.findConfigs(Client.DATA_PATH.toFile(), FILE_NAME);

        IResourceAccessor.process(accessors, accessor -> {
            var cfg = accessor.as(CODEC);
            if (cfg != null)
                initFromConfig(cfg);
        });
    }

    private static void initFromConfig(final List<DimensionConfig> cfg) {
        cfg.forEach(DimensionLibrary::register);
    }

    private static DimensionConfig getData(final DimensionConfig entry) {
        final Optional<DimensionConfig> result = cache.stream().filter(e -> e.equals(entry)).findFirst();
        if (result.isPresent())
            return result.get();
        cache.add(entry);
        return entry;
    }

    private static void register(final DimensionConfig entry) {
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

    public static DimensionInfo getData(final World world) {
        RegistryKey<World> key = world.getRegistryKey();
        DimensionInfo dimInfo = configs.get(key);

        if (dimInfo == null) {
            DimensionConfig config = null;
            Identifier location = key.getValue();
            for (final DimensionConfig e : cache)
                if (e.dimensionId.equals(location)) {
                    config = e;
                    break;
                }

            configs.put(key, dimInfo = new DimensionInfo(world, config));
        }
        return dimInfo;
    }

    public static Stream<String> dump() {
        return cache.stream().map(Object::toString).sorted();
    }
}