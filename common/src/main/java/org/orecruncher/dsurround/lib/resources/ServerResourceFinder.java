// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.function.SingletonSupplier;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Finds data files, such as tags, in the jars of the installed mods (data/(namespace)/...). When connected to a
 * server that doesn't send the mod's tags (a vanilla server), this is where they are filled in from.
 */
public class ServerResourceFinder extends AbstractResourceFinder {

    private static final String DATA = PackType.SERVER_DATA.getDirectory();
    // The installed mods with a data folder. Mods can't come or go during a session, so these are found once.
    private static final Supplier<List<String>> INSTALLED_DATA_MODS = SingletonSupplier.from(ServerResourceFinder::findInstalledDataMods);

    private final IModFiles files;
    private final Supplier<? extends Collection<String>> mods;

    protected ServerResourceFinder(IModLog logger) {
        this(logger, ContainerManager.resolve(IModFiles.class), INSTALLED_DATA_MODS);
    }

    /**
     * @param files the mods' files
     * @param mods  the ids of the mods to look in
     */
    ServerResourceFinder(IModLog logger, IModFiles files, Supplier<? extends Collection<String>> mods) {
        super(logger);
        this.files = files;
        this.mods = mods;
    }

    /**
     * @param assetPath what to find, as namespace:path (e.g. "c:tags/block/glass_blocks")
     */
    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String assetPath) {
        var location = Identifier.tryParse(assetPath);
        if (location == null) {
            this.logger.warn("Not a resource location: %s", assetPath);
            return List.of();
        }

        var relative = DATA + "/" + withJsonExtension(location.getNamespace() + "/" + location.getPath());
        var results = new ObjectArray<DiscoveredResource<T>>();
        for (var mod : this.mods.get()) {
            if (this.files.has(mod, relative))
                this.readInto(location, location.getNamespace(), mod + ":" + relative, () -> this.files.open(mod, relative), codec, results);
        }
        return results;
    }

    private static List<String> findInstalledDataMods() {
        var files = ContainerManager.resolve(IModFiles.class);
        return Platform.getMods().stream().map(Mod::getModId).filter(id -> files.hasFolder(id, DATA)).toList();
    }
}
