package org.orecruncher.dsurround.lib.block;

import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Special property collection that can be used to perform fuzzy matching against other
 * property collections.  Used for partial matching.
 */
public class BlockStateProperties {

    private static final IModLog LOGGER = Library.LOGGER;

    public static final BlockStateProperties NONE = new BlockStateProperties();

    private final List<Property.Value<?>> props;

    private BlockStateProperties() {
        this.props = ImmutableList.of();
    }

    public BlockStateProperties(final BlockState state) {
        this(state.getValues().toList());
    }

    public BlockStateProperties(final List<Property.Value<?>> properties) {
        this.props = properties;
    }

    /**
     * Determines if the property values of this collection are a subset of the properties of the provided
     * BlockState value.
     *
     * @param state The BlockState that is to be evaluated
     * @return true if all the property values in the collection match the BlockState; false otherwise
     */
    public boolean matches(final BlockState state) {
        try {
            for (final var pv : this.props) {
                final Comparable<?> comp = state.getValue(pv.property());
                if (!comp.equals(pv.value()))
                    return false;
            }
            return true;
        } catch (final Throwable ignored) {
            // A property in this list does not exist in the target list.  This is highly unusual because it is
            // expected that this list is a subset of what could be found in a blockstate for the same block instance.
            LOGGER.warn("Property list %s does not correspond the properties in %s", this.toString(), new MatchOnBlockState(state).toString());
        }
        return false;
    }

    /**
     * Determines if the property values are a subset of the values specifed in the target BlockStateProperties
     * collection.
     *
     * @param props Target BlockStateProperties collection to evaluate
     * @return true if all the property values in the collection match BlockStateProperties; false otherwise
     */
    public boolean matches(final BlockStateProperties props) {
        return matches(props.props);
    }

    /**
     * Determines if the property values are a subset of the specified properties map.
     *
     * @param m Property map to evaluate
     * @return true if all the property values in the collection are present in the map; false otherwise
     */
    public boolean matches(final List<Property.Value<?>> m) {
        try {
            if (this.props == m)
                return true;
            if (this.props.size() > m.size())
                return false;
            for (final var pv : this.props) {
                for (var entry : m) {
                    if (entry.property().equals(pv.property())) {
                        if (!entry.value().equals(pv.value())) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (final Throwable ignored) {
            // This is probable in that a property in this list does not exist in the target list.  Can happen if
            // the two lists are for fuzzy matching against blockstate and the sets are disjointed.
        }
        return false;
    }

    @Override
    public int hashCode() {
        int code = 0;
        for (final var pv : this.props) {
            code = code * 31 + pv.property().hashCode();
            code = code * 31 + pv.value().hashCode();
        }
        return code;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof final BlockStateProperties e))
            return false;
        return this.props.size() == e.props.size() && matches(e.props);
    }

    public String getFormattedProperties() {
        if (this.props.isEmpty())
            return Strings.EMPTY;
        final String txt = this.props.stream()
                .map(kvp -> kvp.valueName() + "=" + kvp.value())
                .collect(Collectors.joining(","));
        return "[" + txt + "]";
    }

    public String toString() {
        return "BlockStateProperties{" + getFormattedProperties() + "}";
    }

}