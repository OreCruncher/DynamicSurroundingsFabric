package org.orecruncher.dsurround.lib.config;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
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
        Text txt = new TranslatableText(key);
        if (txt.asString().equals(key)) {
            var comment = this.getComment();
            if (comment != null)
                txt = new LiteralText(comment);
        }
        result.add(txt);
        return result;
    }

    /**
     * Retrieve the comment, if any, associated with the property.
     */
    public @Nullable String getComment() {
        return null;
    }

    /**
     * Retrieves the value from the config and sets it to current.
     */
    public abstract void load();

    /**
     * Takes the current value and stores it back into the config
     */
    public abstract void save();

    public static class PropertyGroup extends ConfigElement<String> {

        private final Class<?> clazz;
        private final Collection<ConfigElement<?>> children;

        PropertyGroup(Class<?> clazz, String translationKey, Collection<ConfigElement<?>> children) {
            super(translationKey);

            this.clazz = clazz;
            this.children = children;
        }

        public Collection<ConfigElement<?>> getChildren() {
            return this.children;
        }

        @Override
        public void load() {
            for (var child : this.children)
                child.load();
        }

        @Override
        public void save() {
            for (var child : this.children)
                child.save();
        }

        @Override
        public @Nullable String getComment() {
            var comment = this.clazz.getAnnotation(ConfigurationData.Comment.class);
            return comment != null ? comment.value() : null;
        }

    }

    public static class PropertyValue<T> extends ConfigElement<T> {

        // Used to access the authoritative value of the property
        private final ConfigValue<T> wrapper;

        private final T defaultValue;
        private T currentValue;

        PropertyValue(String translationKey, ConfigValue<T> wrapper) {
            super(translationKey);
            this.wrapper = wrapper;
            this.setCurrentValue(wrapper.get());
            this.defaultValue = this.currentValue;
        }

        public T getDefaultValue() {
            return this.defaultValue;
        }

        public T getCurrentValue() {
            return this.currentValue;
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

        public void setCurrentValue(T value) {
            this.currentValue = this.clamp(value);
        }

        protected T clamp(T value) {
            return value;
        }

        @Override
        public List<Text> getTooltip() {
            var result = super.getTooltip();

            if (this.isClientRestartRequired())
                result.add(new TranslatableText("dsurround.config.tooltip.clientRestartRequired"));
            else if (this.isWorldRestartRequired())
                result.add(new TranslatableText("dsurround.config.tooltip.worldRestartRequired"));

            var dv = this.wrapper.getAnnotation(ConfigurationData.DefaultValue.class);
            if (dv != null)
                result.add(new TranslatableText("dsurround.config.tooltip.defaultValue", this.defaultValue));

            return result;
        }

        @Override
        public @Nullable String getComment() {
            var comment = this.wrapper.getAnnotation(ConfigurationData.Comment.class);
            return comment != null ? comment.value() : null;
        }

        @Override
        public void load() {
            this.setCurrentValue(this.wrapper.get());
        }

        @Override
        public void save() {
            this.wrapper.set(this.currentValue);
        }

    }

    public static class BooleanValue extends PropertyValue<Boolean> {

        BooleanValue(String translationKey, ConfigValue<Boolean> wrapper) {
            super(translationKey, wrapper);
        }
    }

    public static class StringValue extends PropertyValue<String> {

        StringValue(String translationKey, ConfigValue<String> wrapper) {
            super(translationKey, wrapper);
        }
    }

    public static class IntegerValue extends PropertyValue<Integer> {

        private int minValue = Integer.MIN_VALUE;
        private int maxValue = Integer.MAX_VALUE;

        IntegerValue(String translationKey, ConfigValue<Integer> wrapper) {
            super(translationKey, wrapper);
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
                result.add(new TranslatableText("dsurround.config.tooltip.range", this.getMinValue(), this.getMaxValue()));
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

        DoubleValue(String translationKey, ConfigValue<Double> wrapper) {
            super(translationKey, wrapper);
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
                result.add(new TranslatableText("dsurround.config.tooltip.range", this.getMinValue(), this.getMaxValue()));
            return result;
        }

        @Override
        protected Double clamp(Double val) {
            return MathHelper.clamp(val, this.minValue, this.maxValue);
        }

    }

}
