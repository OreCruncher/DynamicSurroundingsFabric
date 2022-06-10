package org.orecruncher.dsurround.lib.config;

import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ConfigElement<T> {

    private final String elementNameKey;

    ConfigElement(String elementNameKey) {
        this.elementNameKey = elementNameKey;
    }

    public String getElementNameKey() {
        return this.elementNameKey;
    }

    public String getElementNameTooltipKey() {
        return this.elementNameKey + ".tooltip";
    }

    public List<Text> getTooltip() {
        var result = new ArrayList<Text>();
        var key = this.getElementNameTooltipKey();
        Text txt = Text.translatable(key);
        if (txt.toString().equals(key)) {
            var comment = this.getComment();
            if (comment != null)
                txt = Text.of(comment);
        }
        result.add(txt);
        return result;
    }

    public boolean isHidden() {
        return false;
    }

    /**
     * Retrieve the comment, if any, associated with the property.
     */
    public @Nullable String getComment() {
        return null;
    }

    public static class PropertyGroup extends ConfigElement<String> {

        private final ConfigValue<?> wrapper;
        private final Collection<ConfigElement<?>> children;

        PropertyGroup(ConfigValue<?> wrapper, String translationKey, Collection<ConfigElement<?>> children) {
            super(translationKey);

            this.wrapper = wrapper;
            this.children = children;
        }

        public Object getInstance(Object instance) {
            return this.wrapper.get(instance);
        }

        @Override
        public boolean isHidden() {
            return this.wrapper.getAnnotation(ConfigurationData.Hidden.class) != null;
        }

        public Collection<ConfigElement<?>> getChildren() {
            return this.children;
        }

        @Override
        public @Nullable String getComment() {
            var comment = this.wrapper.getAnnotation(ConfigurationData.Comment.class);
            return comment != null ? comment.value() : null;
        }

    }

    public static class PropertyValue<T> extends ConfigElement<T> {

        // Used to access the authoritative value of the property
        private final ConfigValue<T> wrapper;

        private final T defaultValue;

        PropertyValue(Object instance, String translationKey, ConfigValue<T> wrapper) {
            super(translationKey);
            this.wrapper = wrapper;
            this.defaultValue = wrapper.get(instance);
        }

        public T getDefaultValue() {
            return this.defaultValue;
        }

        public T getCurrentValue(Object instance) {
            return this.wrapper.get(instance);
        }

        public boolean isClientRestartRequired() {
            var restart = this.wrapper.getAnnotation(ConfigurationData.RestartRequired.class);
            return restart != null && restart.client();
        }

        public boolean isWorldRestartRequired() {
            var restart = this.wrapper.getAnnotation(ConfigurationData.RestartRequired.class);
            return restart != null && !restart.client();
        }

        public boolean isRestartRequired() {
            return this.wrapper.getAnnotation(ConfigurationData.RestartRequired.class) != null;
        }

        public boolean useSlider() {
            return this.wrapper.getAnnotation(ConfigurationData.Slider.class) != null;
        }

        @Override
        public boolean isHidden() {
            return this.wrapper.getAnnotation(ConfigurationData.Hidden.class) != null;
        }

        public void setCurrentValue(Object instance, T value) {
            this.wrapper.set(instance, this.clamp(value));
        }

        protected T clamp(T value) {
            return value;
        }

        @Override
        public List<Text> getTooltip() {
            var result = super.getTooltip();

            if (this.isClientRestartRequired())
                result.add(Text.translatable("dsurround.config.tooltip.clientRestartRequired"));
            else if (this.isWorldRestartRequired())
                result.add(Text.translatable("dsurround.config.tooltip.worldRestartRequired"));

            var dv = this.wrapper.getAnnotation(ConfigurationData.DefaultValue.class);
            if (dv != null)
                result.add(Text.translatable("dsurround.config.tooltip.defaultValue", this.defaultValue));

            return result;
        }

        @Override
        public @Nullable String getComment() {
            var comment = this.wrapper.getAnnotation(ConfigurationData.Comment.class);
            return comment != null ? comment.value() : null;
        }

    }

    public static class BooleanValue extends PropertyValue<Boolean> {

        BooleanValue(Object instance, String translationKey, ConfigValue<Boolean> wrapper) {
            super(instance, translationKey, wrapper);
        }
    }

    public static class StringValue extends PropertyValue<String> {

        StringValue(Object instance, String translationKey, ConfigValue<String> wrapper) {
            super(instance, translationKey, wrapper);
        }
    }

    public static class IntegerValue extends PropertyValue<Integer> {

        private int minValue = Integer.MIN_VALUE;
        private int maxValue = Integer.MAX_VALUE;

        IntegerValue(Object instance, String translationKey, ConfigValue<Integer> wrapper) {
            super(instance, translationKey, wrapper);
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

        public boolean hasRange() {
            return this.minValue != Integer.MIN_VALUE || this.maxValue != Integer.MAX_VALUE;
        }

        @Override
        public List<Text> getTooltip() {
            var result = super.getTooltip();
            if (this.hasRange())
                result.add(Text.translatable("dsurround.config.tooltip.range", this.getMinValue(), this.getMaxValue()));
            return result;
        }

        @Override
        protected Integer clamp(Integer val) {
            return MathHelper.clamp(val, this.minValue, this.maxValue);
        }

    }

    public static class DoubleValue extends PropertyValue<Double> {

        private double minValue = Double.MIN_VALUE;
        private double maxValue = Double.MAX_VALUE;

        DoubleValue(Object instance, String translationKey, ConfigValue<Double> wrapper) {
            super(instance, translationKey, wrapper);
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

        public boolean hasRange() {
            return this.minValue != Double.MIN_VALUE || this.maxValue != Double.MAX_VALUE;
        }

        @Override
        public List<Text> getTooltip() {
            var result = super.getTooltip();
            if (this.hasRange())
                result.add(Text.translatable("dsurround.config.tooltip.range", this.getMinValue(), this.getMaxValue()));
            return result;
        }

        @Override
        protected Double clamp(Double val) {
            return MathHelper.clamp(val, this.minValue, this.maxValue);
        }

    }

    public static class EnumValue extends PropertyValue<Enum<?>> {

        private final Class<? extends Enum<?>> enumClass;

        EnumValue(Class<? extends Enum<?>> enumClass, Object instance, String translationKey, ConfigValue<Enum<?>> wrapper) {
            super(instance, translationKey, wrapper);

            this.enumClass = enumClass;
        }

        public Class<? extends Enum<?>> getEnumClass() {
            return this.enumClass;
        }
    }

}
