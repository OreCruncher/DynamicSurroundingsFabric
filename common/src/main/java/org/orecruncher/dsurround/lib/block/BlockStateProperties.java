package org.orecruncher.dsurround.lib.block;

import com.google.common.collect.ImmutableMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A set of block state property values, used to match states that have at least those values (a partial match:
 * properties not listed can be anything).
 * <p>
 * Equality is exact: two collections are equal when they hold the same properties with the same values, whatever
 * order they were built in.
 */
final class BlockStateProperties {

    static final BlockStateProperties NONE = new BlockStateProperties(Map.of());

    // An ImmutableMap whatever map was passed in, so equals/hashCode follow java.util.Map's rules (and so don't
    // depend on the order entries were added, or on the source map's implementation)
    private final Map<Property<?>, Comparable<?>> props;

    BlockStateProperties(final BlockState state) {
        this(valuesOf(state));
    }

    BlockStateProperties(final Map<Property<?>, Comparable<?>> props) {
        this.props = ImmutableMap.copyOf(props);
    }

    /**
     * The state's property values, in the order the state lists them.
     */
    private static Map<Property<?>, Comparable<?>> valuesOf(final BlockState state) {
        final Map<Property<?>, Comparable<?>> values = new LinkedHashMap<>();
        state.getValues().forEach(v -> values.put(v.property(), v.value()));
        return values;
    }

    /**
     * True if the state has every property in this collection with the same value. A state that lacks one of the
     * properties (a different block) doesn't match.
     */
    boolean matches(final BlockState state) {
        for (final Map.Entry<Property<?>, Comparable<?>> kvp : this.props.entrySet()) {
            final Property<?> property = kvp.getKey();
            if (!state.hasProperty(property) || !state.getValue(property).equals(kvp.getValue()))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return this.props.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || (obj instanceof final BlockStateProperties other && this.props.equals(other.props));
    }

    /**
     * The properties in block state syntax, as in {@code [axis=y,waterlogged=false]}, or "" if there are none. Values
     * use their serialized names, so the text can be parsed back.
     */
    String getFormattedProperties() {
        if (this.props.isEmpty())
            return "";
        return this.props.entrySet().stream()
                .map(kvp -> kvp.getKey().getName() + "=" + serializedName(kvp.getKey(), kvp.getValue()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String serializedName(final Property<T> property, final Comparable<?> value) {
        return property.getName((T) value);
    }

    @Override
    public String toString() {
        return "BlockStateProperties{" + this.getFormattedProperties() + "}";
    }
}
