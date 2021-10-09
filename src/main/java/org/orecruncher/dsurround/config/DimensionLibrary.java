package org.orecruncher.dsurround.config;

import com.google.gson.reflect.TypeToken;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.DimensionConfig;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.lib.validation.ListValidator;
import org.orecruncher.dsurround.lib.validation.Validators;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Stream;

public final class DimensionLibrary {

    private static final IModLog LOGGER = Client.LOGGER.createChild(DimensionLibrary.class);
    private static final Type dimensionType = TypeToken.getParameterized(List.class, DimensionConfig.class).getType();

    private static final ObjectArray<DimensionConfig> cache = new ObjectArray<>();
    private static final Map<RegistryKey<World>, DimensionInfo> configs = new HashMap<>();

    static {
        Validators.registerValidator(dimensionType, new ListValidator<DimensionConfig>());
    }

    public static void load() {
        configs.clear();
        cache.clear();
        final Collection<IResourceAccessor> configs = ResourceUtils.findConfigs(Client.DATA_PATH.toFile(), "dimensions.json");
        IResourceAccessor.process(configs, accessor -> initFromConfig(accessor.as(dimensionType)));
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
            if (data.dimensionId == null)
                data.dimensionId = entry.dimensionId;
            if (entry.hasHaze != null)
                data.hasHaze = entry.hasHaze;
            if (entry.cloudHeight != null)
                data.cloudHeight = entry.cloudHeight;
            if (entry.seaLevel != null)
                data.seaLevel = entry.seaLevel;
            if (entry.skyHeight != null)
                data.skyHeight = entry.skyHeight;
        }
    }

    public static DimensionInfo getData(final World world) {
        RegistryKey<World> key = world.getRegistryKey();
        DimensionInfo dimInfo = configs.get(key);

        if (dimInfo == null) {
            DimensionConfig config = null;
            Identifier location = key.getValue();
            for (final DimensionConfig e : cache)
                if (e.dimensionId.equals(location.toString())) {
                    config = e;
                    break;
                }

            configs.put(key, dimInfo = new DimensionInfo(world, config));
        }
        return dimInfo;
    }

    public static Stream<String> dump()
    {
        return cache.stream().map(Object::toString).sorted();
    }
}