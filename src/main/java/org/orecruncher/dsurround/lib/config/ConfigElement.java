package org.orecruncher.dsurround.lib.config;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.Localization;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Optional;

public abstract class ConfigElement<T> {

    private static final Style STYLE_RANGE = Style.EMPTY.withColor(ColorPalette.CORN_FLOWER_BLUE);
    private static final Style STYLE_DEFAULT = Style.EMPTY.withColor(ColorPalette.TAN);
    private static final Style STYLE_MISSING = Style.EMPTY.withColor(ColorPalette.RED).withItalic(true);

    private final String languageKey;
    private final ElementAccessor<T> field;

    ConfigElement(String elementNameKey, Field field) {
        this.languageKey = elementNameKey;
        this.field = new ElementAccessor<>(field);
    }

    public String getLanguageKey() {
        return this.languageKey;
    }

    public String getTooltipLanguageKey() {
        return this.languageKey + ".tooltip";
    }

    public Component getTooltip(Style style) {
        // Get tooltip data from language file. If not present, fall back to the Comment annotation
        // in the config model. If the property does not have a Comment annotation, use the resource key.
        var key = this.getTooltipLanguageKey();
        var resourceText = Localization.loadIfPresent(key);

        if (resourceText.isEmpty()) {
            resourceText = this.getComment();
        }

        return resourceText
                .map(txt -> Component.literal(txt).withStyle(style))
                .orElse(Component.literal("MISSING: " + key).withStyle(STYLE_MISSING));
    }

    public boolean isHidden() {
        return this.hasAnnotation(ConfigurationData.Hidden.class);
    }

    /**
     * Retrieve the comment, if any, associated with the property.
     */
    public Optional<String> getComment() {
        var comment = this.getAnnotation(ConfigurationData.Comment.class);
        return comment.map(ConfigurationData.Comment::value);
    }

    protected T get(Object instance) {
        return this.field.get(instance);
    }

    protected void set(Object instance, T val) {
        this.field.set(instance, val);
    }

    protected <A extends Annotation> Optional<A> getAnnotation(Class<A> annotation) {
        return Optional.ofNullable(this.field.getAnnotation(annotation));
    }

    protected <A extends Annotation> boolean hasAnnotation(Class<A> annotation) {
        return this.getAnnotation(annotation).isPresent();
    }

    public static class PropertyGroup extends ConfigElement<Object> {

        private final Collection<ConfigElement<?>> children;

        PropertyGroup(String translationKey, Collection<ConfigElement<?>> children, Field field) {
            super(translationKey, field);

            this.children = children;
        }

        public Object getInstance(Object instance) {
            return this.get(instance);
        }

        public Collection<ConfigElement<?>> getChildren() {
            return this.children;
        }

    }

    public static class PropertyValue<T> extends ConfigElement<T> {

        private final T defaultValue;

        PropertyValue(Object instance, String translationKey, Field field) {
            super(translationKey, field);

            this.defaultValue = this.get(instance);
        }

        public <V> Binder<V> createBinder(Object instance) {
            return new Binder<>(this, instance);
        }

        public T defaultValue() {
            return this.defaultValue;
        }

        public T getValue(Object instance) {
            return this.get(instance);
        }

        public void setValue(Object instance, T value) {
            this.set(instance, this.clamp(value));
        }

        public boolean isRestartRequired() {
            var annotation = this.getAnnotation(ConfigurationData.RestartRequired.class);
            return annotation.map(ConfigurationData.RestartRequired::client).orElse(false);
        }

        public boolean isWorldRestartRequired() {
            var annotation = this.getAnnotation(ConfigurationData.RestartRequired.class);
            return annotation.map(a -> !a.client()).orElse(false);
        }

        public boolean isAssetReloadRequired() {
            return this.hasAnnotation(ConfigurationData.AssetReloadRequired.class);
        }

        public boolean useSlider() {
            return this.hasAnnotation(ConfigurationData.Slider.class);
        }


        protected T clamp(T value) {
            return value;
        }

        public Component getDefaultValueTooltip() {
            return Component.translatable("dsurround.config.tooltip.defaultValue", this.defaultValue).withStyle(STYLE_DEFAULT);
        }
    }

    public static class BooleanValue extends PropertyValue<Boolean> {

        private static final Component YES = Component.translatable("gui.yes").withColor(ColorPalette.GREEN.getValue());
        private static final Component NO = Component.translatable("gui.no").withColor(ColorPalette.RED.getValue());

        BooleanValue(Object instance, String translationKey, Field field) {
            super(instance, translationKey, field);
        }

        @Override
        public Component getDefaultValueTooltip() {
            var text = this.defaultValue() ? YES : NO;
            return Component.translatable("dsurround.config.tooltip.defaultValue", text).withStyle(STYLE_DEFAULT);
        }

    }

    public static class StringValue extends PropertyValue<String> {

        StringValue(Object instance, String translationKey, Field field) {
            super(instance, translationKey, field);
        }
    }

    public static class IntegerValue extends PropertyValue<Integer> implements IRangeTooltip {

        private int minValue = Integer.MIN_VALUE;
        private int maxValue = Integer.MAX_VALUE;

        IntegerValue(Object instance, String translationKey, Field field) {
            super(instance, translationKey, field);
        }

        public void setRange(int min, int max) {
            this.minValue = min;
            this.maxValue = max;
        }

        public int getMinValue() {
            return this.minValue;
        }

        public int getMaxValue() {
            return this.maxValue;
        }

        @Override
        public boolean hasRange() {
            return this.minValue != Integer.MIN_VALUE || this.maxValue != Integer.MAX_VALUE;
        }

        @Override
        public Component getRangeTooltip() {
            return Component.translatable("dsurround.config.tooltip.range", this.getMinValue(), this.getMaxValue()).withStyle(STYLE_RANGE);
        }

        @Override
        protected Integer clamp(Integer val) {
            return Mth.clamp(val, this.minValue, this.maxValue);
        }

    }

    public static class DoubleValue extends PropertyValue<Double> implements IRangeTooltip {

        private double minValue = Double.MIN_VALUE;
        private double maxValue = Double.MAX_VALUE;

        DoubleValue(Object instance, String translationKey, Field field) {
            super(instance, translationKey, field);
        }

        public void setRange(double min, double max) {
            this.minValue = min;
            this.maxValue = max;
        }

        public double getMinValue() {
            return this.minValue;
        }

        public double getMaxValue() {
            return this.maxValue;
        }

        @Override
        public boolean hasRange() {
            return this.minValue != Double.MIN_VALUE || this.maxValue != Double.MAX_VALUE;
        }

        @Override
        public Component getRangeTooltip() {
            return Component.translatable("dsurround.config.tooltip.range", this.getMinValue(), this.getMaxValue()).withStyle(STYLE_RANGE);
        }

        @Override
        protected Double clamp(Double val) {
            return Mth.clamp(val, this.minValue, this.maxValue);
        }

    }

    public static class EnumValue extends PropertyValue<Enum<?>> {

        private final Class<? extends Enum<?>> enumClass;

        EnumValue(Class<? extends Enum<?>> enumClass, Object instance, String translationKey, Field field) {
            super(instance, translationKey, field);

            this.enumClass = enumClass;
        }

        public Class<? extends Enum<?>> getEnumClass() {
            return this.enumClass;
        }
    }

    public interface IRangeTooltip {

        boolean hasRange();

        Component getRangeTooltip();
    }

}