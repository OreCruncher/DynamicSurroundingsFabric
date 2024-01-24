package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.nio.file.Path;
import java.util.Collection;

public interface IResourceFinder<T> {

    default IPlatform getPlatform() {
        return Library.PLATFORM;
    }

    Collection<DiscoveredResource<T>> find(String assetPath);

    Collection<DiscoveredResource<T>> find(ResourceLocation resource);

    static <A> IResourceFinder<A> createClientFinder(Codec<A> codec, String pathPrefix) {
        return new ClientResourceFinder<>(codec, pathPrefix);
    }

    static IResourceFinder<TagFile> createFinderForTag(TagKey<?> tagKey, Path folderLocation) {
        Collection<IResourceFinder<TagFile>> finders = new ObjectArray<>(3);
        var tagFolder = TagManager.getTagDir(tagKey.registry());
        finders.add(new ServerResourceFinder<>(TagFile.CODEC, tagFolder));
        finders.add(new ClientResourceFinder<>(TagFile.CODEC, "dsconfigs/" + tagFolder));
        finders.add(new DiskResourceFinder<>(TagFile.CODEC, tagFolder, folderLocation));
        return new CompositeResourceFinder<>(finders);
    }

    static <A> IResourceFinder<A> createFinderForModConfiguration(Codec<A> codec, Path folderLocation, String pathPrefix) {
        String modifiedPathPrefix = "dsconfigs";
        if (StringUtils.isNotEmpty(pathPrefix))
            modifiedPathPrefix = modifiedPathPrefix + "/" + pathPrefix;
        var packFinder = new ClientResourceFinder<>(codec, modifiedPathPrefix);
        var diskFinder = new DiskResourceFinder<>(codec, pathPrefix, folderLocation);
        return new CompositeResourceFinder<>(packFinder, diskFinder);
    }

}
