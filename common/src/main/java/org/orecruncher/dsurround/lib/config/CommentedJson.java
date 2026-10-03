package org.orecruncher.dsurround.lib.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.config.ConfigurationData.Comment;
import org.orecruncher.dsurround.lib.config.ConfigurationData.DoubleSlider;
import org.orecruncher.dsurround.lib.config.ConfigurationData.DoubleRange;
import org.orecruncher.dsurround.lib.config.ConfigurationData.IntegerRange;
import org.orecruncher.dsurround.lib.config.ConfigurationData.Slider;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes configuration objects as JSON with comments. The two go together: what {@link #write} produces
 * has comments in it, so {@link #read} must accept them.
 * <p>
 * Writing lays the JSON out as Gson's pretty printing does, with {@code //} comments above each field: its
 * {@link Comment}, then its range ({@link IntegerRange}, {@link Slider}, {@link DoubleRange}, {@link DoubleSlider}),
 * the values an enum can take, and the default value. Gson builds the JSON, so it is the same as Gson would write; only the comments are added.
 * <p>
 * A field without a comment of its own uses its type's only if the type's comment is marked
 * {@link Comment#inherit()}. Defaults come from a newly constructed instance of the object, so they are only shown
 * when it has a no-argument constructor, and only for simple values (not objects or lists).
 * <p>
 * Reading accepts {@code //}, {@code /* *}{@code /} and {@code #} comments, so files edited by hand load too. The
 * comments are rebuilt each time the file is written, so comments added by hand are not kept.
 */
final class CommentedJson {

    // Not pretty printing: the layout is done here. Serialization settings (nulls left out, and so on) are Gson's
    // defaults, as they were when Gson wrote the files itself.
    private static final Gson GSON = new Gson();
    private static final String INDENT = "  ";

    private CommentedJson() {
    }

    /**
     * Reads an object of type {@code type} from {@code reader}, allowing comments.
     *
     * @return the object, or null if the input is empty
     * @throws JsonParseException if the input isn't valid JSON for the type, or has anything after it
     */
    static <T> @Nullable T read(Reader reader, Class<T> type) {
        var json = new JsonReader(reader);
        // Lenient parsing is what allows comments. Gson's fromJson() turns it on by default too, but depending on
        // that would tie the config files to a default; newer Gson replaces this with setStrictness(LENIENT).
        json.setLenient(true);
        T result = GSON.fromJson(json, type);
        // fromJson(Reader) checks for content after the object; fromJson(JsonReader) leaves that to the caller
        try {
            if (result != null && json.peek() != JsonToken.END_DOCUMENT)
                throw new JsonSyntaxException("Unexpected content after the JSON object");
        } catch (IOException e) {
            throw new JsonIOException(e);
        }
        return result;
    }

    /**
     * {@code value} as commented JSON.
     */
    static String write(Object value) {
        var out = new StringBuilder();
        new CommentedJson.Writer(GSON, out).element(GSON.toJsonTree(value), value, defaultsFor(value), 0);
        return out.toString();
    }

    /**
     * A new instance of {@code value}'s class, holding its defaults; null if one can't be made.
     */
    private static @Nullable Object defaultsFor(Object value) {
        try {
            return ConfigProcessor.createPrototype(value.getClass());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record Writer(Gson gson, StringBuilder out) {

        void element(JsonElement element, @Nullable Object source, @Nullable Object defaults, int depth) {
            if (element instanceof JsonObject object)
                this.object(object, source, defaults, depth);
            else if (element instanceof JsonArray array)
                this.array(array, depth);
            else
                // Primitives and null. Unlike a default Gson, this doesn't escape HTML characters, which keeps text
                // such as "<" readable; both forms read back the same.
                this.out.append(element);
        }

        void object(JsonObject object, @Nullable Object source, @Nullable Object defaults, int depth) {
            if (object.isEmpty()) {
                this.out.append("{}");
                return;
            }

            this.out.append("{\n");
            int remaining = object.size();
            for (var entry : object.entrySet()) {
                var value = entry.getValue();
                var field = source == null ? null : findField(source.getClass(), entry.getKey());
                var fieldDefault = valueOf(field, defaults);

                for (var line : this.commentLines(field, value, fieldDefault))
                    this.indent(depth + 1).append("// ").append(line).append('\n');

                this.indent(depth + 1).append(new JsonPrimitive(entry.getKey())).append(": ");
                this.element(value, valueOf(field, source), fieldDefault, depth + 1);
                if (--remaining > 0)
                    this.out.append(',');
                this.out.append('\n');
            }
            this.indent(depth).append('}');
        }

        void array(JsonArray array, int depth) {
            if (array.isEmpty()) {
                this.out.append("[]");
                return;
            }

            this.out.append("[\n");
            for (int i = 0; i < array.size(); i++) {
                this.indent(depth + 1);
                // Array elements have no fields to take comments from
                this.element(array.get(i), null, null, depth + 1);
                if (i < array.size() - 1)
                    this.out.append(',');
                this.out.append('\n');
            }
            this.indent(depth).append(']');
        }

        StringBuilder indent(int depth) {
            return this.out.repeat(INDENT, depth);
        }

        /**
         * The comment lines for a field: its comment, then a line with its range and default, either of which
         * may be missing.
         */
        List<String> commentLines(@Nullable Field field, JsonElement value, @Nullable Object fieldDefault) {
            var lines = new ArrayList<String>();
            if (field == null)
                return lines;

            var comment = commentFor(field);
            if (comment != null) {
                for (var line : comment.split("\\R"))
                    lines.add(line.stripTrailing());
            }

            // Ranges, choices and defaults only mean something for simple values
            if (value.isJsonPrimitive()) {
                var details = new ArrayList<String>(3);
                var range = rangeOf(field);
                if (range != null)
                    details.add(range);
                var values = this.enumValuesOf(field, fieldDefault);
                if (values != null)
                    details.add(values);
                if (fieldDefault != null)
                    details.add("default: " + this.gson.toJsonTree(fieldDefault));
                if (!details.isEmpty()) {
                    var line = String.join(", ", details);
                    lines.add(Character.toUpperCase(line.charAt(0)) + line.substring(1));
                }
            }
            return lines;
        }

        /**
         * The values an enum field can take, as "values: ALPHA, BETA", or null if it isn't an enum. Each is shown
         * as Gson writes it, so it can be copied into the file.
         */
        @Nullable String enumValuesOf(Field field, @Nullable Object fieldDefault) {
            Class<?> type = field.getType().isEnum() ? field.getType()
                    : fieldDefault instanceof Enum<?> e ? e.getDeclaringClass() : null;
            if (type == null)
                return null;
            var names = new ArrayList<String>();
            for (var constant : type.getEnumConstants())
                names.add(this.gson.toJsonTree(constant).getAsString());
            return "values: " + String.join(", ", names);
        }
    }

    /**
     * The comment for a field: its own, or its type's if that is marked to be inherited. Null if there is none, or
     * it is blank.
     */
    static @Nullable String commentFor(Field field) {
        var comment = field.getAnnotation(Comment.class);
        if (comment == null) {
            var typeComment = field.getType().getAnnotation(Comment.class);
            if (typeComment != null && typeComment.inherit())
                comment = typeComment;
        }
        return comment == null || comment.value().isBlank() ? null : comment.value();
    }

    /**
     * The field's range, as "range: 0 - 10", or "minimum: 0" when it has no upper limit, and a double slider's step,
     * as "range: 0 - 1, step: 0.1". Null if it has no range.
     */
    static @Nullable String rangeOf(Field field) {
        var intRange = field.getAnnotation(IntegerRange.class);
        if (intRange != null)
            return intRange.max() == Integer.MAX_VALUE
                    ? "minimum: " + intRange.min()
                    : "range: " + intRange.min() + " - " + intRange.max();

        // A slider's range always has both limits
        var slider = field.getAnnotation(Slider.class);
        if (slider != null)
            return "range: " + slider.min() + " - " + slider.max();

        var doubleSlider = field.getAnnotation(DoubleSlider.class);
        if (doubleSlider != null)
            return "range: " + formatNumber(doubleSlider.min()) + " - " + formatNumber(doubleSlider.max())
                    + ", step: " + formatNumber(doubleSlider.step());

        var doubleRange = field.getAnnotation(DoubleRange.class);
        if (doubleRange != null)
            return doubleRange.max() == Double.MAX_VALUE
                    ? "minimum: " + formatNumber(doubleRange.min())
                    : "range: " + formatNumber(doubleRange.min()) + " - " + formatNumber(doubleRange.max());

        return null;
    }

    /**
     * A range limit as a person would write it: whole numbers without ".0".
     */
    static String formatNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15)
            return Long.toString((long) value);
        return Double.toString(value);
    }

    /**
     * The instance field Gson wrote as {@code name}, searching superclasses too. Gson names fields after
     * themselves, as the configuration classes use no naming annotations.
     */
    private static @Nullable Field findField(Class<?> type, String name) {
        for (var c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                var field = c.getDeclaredField(name);
                if (!Modifier.isStatic(field.getModifiers()))
                    return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /**
     * The value of {@code field} in {@code source}, so a nested object's fields can be found; null if there is no
     * source, or it can't be read.
     */
    private static @Nullable Object valueOf(@Nullable Field field, @Nullable Object source) {
        if (field == null || source == null || !field.trySetAccessible())
            return null;
        try {
            return field.get(source);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
