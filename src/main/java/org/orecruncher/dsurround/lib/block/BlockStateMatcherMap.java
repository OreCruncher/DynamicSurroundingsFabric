package org.orecruncher.dsurround.lib.block;

import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.IMatcher;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Special Map implementation that is implemented with BlockStateMatcher as a key type.  It handles any special
 * processing that may occur because of the fuzziness of BlockState matching.
 *
 * @param <T> Value type of the Map
 */
public final class BlockStateMatcherMap<T> implements Map<IMatcher<BlockState>, T> {

    private final Map<IMatcher<BlockState>, T> map = new Object2ObjectOpenHashMap<>();
    private Supplier<T> defaultValue = () -> null;

    @Nullable
    public T get(final BlockState state) {
        T result = this.map.get(BlockStateMatcher.create(state));
        if (result == null)
            result = this.map.get(BlockStateMatcher.asGeneric(state));
        if (result == null)
            result = this.defaultValue.get();
        return result;
    }

    public void setDefaultValue(final Supplier<T> s) {
        this.defaultValue = s;
    }

    @Override
    public int size() {
        return this.map.size();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.map.containsValue(value);
    }

    @Override
    @Nullable
    public T get(Object key) {
        return this.map.get(key);
    }

    @Override
    @Nullable
    public T put(final IMatcher<BlockState> matcher, final T val) {
        return this.map.put(matcher, val);
    }

    @Override
    @Nullable
    public T remove(Object key) {
        return this.map.remove(key);
    }

    @Override
    public void putAll(@NotNull Map<? extends IMatcher<BlockState>, ? extends T> m) {
        this.map.putAll(m);
    }

    @Override
    public void clear() {
        this.map.clear();
    }

    @Override
    public @NotNull Set<IMatcher<BlockState>> keySet() {
        return this.map.keySet();
    }

    @Override
    public @NotNull Collection<T> values() {
        return this.map.values();
    }

    @Override
    public @NotNull Set<Entry<IMatcher<BlockState>, T>> entrySet() {
        return this.map.entrySet();
    }

    public void put(final String blockName, final T val) {
        try {
            var result = BlockStateMatcher.create(blockName);
            this.put(result, val);
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Unable to put item into map");
        }
    }

    public void put(final BlockState state, final T val) {
        var result = BlockStateMatcher.create(state);
        if (!result.isEmpty())
            put(result, val);
    }

    public void put(final Block block, final T val) {
        var result = BlockStateMatcher.create(block);
        if (!result.isEmpty())
            put(result, val);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(this.map.entrySet().stream().map(Object::toString).collect(Collectors.joining("\n"))).toString();
    }

}