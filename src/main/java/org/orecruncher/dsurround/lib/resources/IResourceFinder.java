package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.nio.file.Path;
import java.util.Collection;

public interface IResourceFinder<T> {

    default IPlatform getPlatform() {
        return Library.getPlatform();
    }

    default ResourceManager getResourceManager() {
        return GameUtils.getResourceManager();
    }

    default Collection<DiscoveredResource<T>> find(String resourcePath) {
        Collection<DiscoveredResource<T>> results = new ObjectArray<>();
        var namespaceList = this.getResourceManager().getNamespaces();
        for (var namespace : namespaceList)
            results.addAll(this.find(new ResourceLocation(namespace, resourcePath)));
        return results;
    }

    Collection<DiscoveredResource<T>> find(ResourceLocation resource);

    static <A> IResourceFinder<A> createFinderForType(PackType type, Codec<A> codec, String pathPrefix) {
        return new PackResourceFinder<>(codec, pathPrefix, type);
    }

    static IResourceFinder<TagFile> createFinderForTag(TagKey<?> tagKey, Path folderLocation) {
        Collection<IResourceFinder<TagFile>> finders = new ObjectArray<>(3);
        var tagFolder = TagManager.getTagDir(tagKey.registry());
        finders.add(new PackResourceFinder<>(TagFile.CODEC, tagFolder, PackType.SERVER_DATA));
        finders.add(new PackResourceFinder<>(TagFile.CODEC, "dsconfigs/" + tagFolder, PackType.CLIENT_RESOURCES));
        finders.add(new DiskResourceFinder<>(TagFile.CODEC, tagFolder, folderLocation));
        return new CompositeResourceFinder<>(finders);
    }

    static <A> IResourceFinder<A> createFinderForModConfiguration(Codec<A> codec, Path folderLocation, String pathPrefix) {
        String modifiedPathPrefix = "dsconfigs";
        if (StringUtils.isNotEmpty(pathPrefix))
            modifiedPathPrefix = modifiedPathPrefix + "/" + pathPrefix;
        var packFinder = new PackResourceFinder<>(codec, modifiedPathPrefix, PackType.CLIENT_RESOURCES);
        var diskFinder = new DiskResourceFinder<>(codec, pathPrefix, folderLocation);
        return new CompositeResourceFinder<>(packFinder, diskFinder);
    }

}
