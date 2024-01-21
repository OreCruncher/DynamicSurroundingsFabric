package org.orecruncher.dsurround.lib.resources;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.util.Collection;
import java.util.Objects;

public class CompositeResourceFinder<T> implements IResourceFinder<T> {

    private final Collection<IResourceFinder<T>> finders;

    CompositeResourceFinder(Collection<IResourceFinder<T>> finders) {
        this.finders = Objects.requireNonNull(finders);
    }

    @SafeVarargs
    public CompositeResourceFinder(IResourceFinder<T>... finders) {
        this.finders = new ObjectArray<>(Objects.requireNonNull(finders));
    }

    @Override
    public Collection<DiscoveredResource<T>> find(ResourceLocation resource) {
        Collection<DiscoveredResource<T>> results = new ObjectArray<>();
        for (var finder : this.finders)
            results.addAll(finder.find(resource));
        return results;
    }
}
