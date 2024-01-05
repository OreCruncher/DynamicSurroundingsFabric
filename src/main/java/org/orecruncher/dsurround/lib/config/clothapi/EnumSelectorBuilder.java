package org.orecruncher.dsurround.lib.config.clothapi;

import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.impl.builders.FieldBuilder;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Special EnumSelectorBuilder that deal with enums anonymously.  Makes for easier gluing between config and
 * menu display.
 */
public class EnumSelectorBuilder extends FieldBuilder<Enum<?>, EnumListEntry<Enum<?>>, EnumSelectorBuilder> {
    private Consumer<Enum<?>> saveConsumer = null;
    private Function<Enum<?>, Optional<Component[]>> tooltipSupplier = e -> Optional.empty();
    private final Enum<?> value;
    private final Class<Enum<?>> clazz;
    private final Function<Enum, Component> enumNameProvider;

    public EnumSelectorBuilder(Component resetButtonKey, Component fieldNameKey, Class<Enum<?>> clazz, Enum<?> value) {
        super(resetButtonKey, fieldNameKey);
        this.enumNameProvider = e -> Component.literal(e.name());
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(value);
        this.value = value;
        this.clazz = clazz;
    }

    public EnumSelectorBuilder setSaveConsumer(Consumer<Enum<?>> saveConsumer) {
        this.saveConsumer = saveConsumer;
        return this;
    }

    public EnumSelectorBuilder setDefaultValue(Enum<?> defaultValue) {
        Objects.requireNonNull(defaultValue);
        this.defaultValue = () -> defaultValue;
        return this;
    }

    public EnumSelectorBuilder setTooltip(Component... tooltip) {
        this.tooltipSupplier = e -> Optional.ofNullable(tooltip);
        return this;
    }

    @NotNull
    public EnumListEntry<Enum<?>> build() {
        EnumListEntry<Enum<?>> entry = new EnumListEntry<>(this.getFieldNameKey(), this.clazz, this.value, this.getResetButtonKey(), this.defaultValue, this.saveConsumer, this.enumNameProvider, null, this.isRequireRestart());
        entry.setTooltipSupplier(() -> this.tooltipSupplier.apply(entry.getValue()));
        if (this.errorSupplier != null) {
            entry.setErrorSupplier(() -> this.errorSupplier.apply(entry.getValue()));
        }

        return entry;
    }
}
