package org.orecruncher.dsurround.lib.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.config.ConfigurationData.MovedFrom;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * Carries values over for properties that have moved (see {@link MovedFrom}), in a config file's JSON before it is
 * read into the configuration's objects.
 */
final class MovedProperties {

    private MovedProperties() {
    }

    /**
     * For each moved property with no value at its new place in {@code root} but one at an old place, copies that
     * value to the new place (from the first old place listed that has one). A value already at the new place is
     * kept.
     *
     * @param onMove told the old and new path of each value carried over
     * @return how many values were carried over
     */
    static int apply(JsonObject root, Collection<ConfigElement<?>> specification, BiConsumer<String, String> onMove) {
        return apply(root, specification, "", onMove);
    }

    private static int apply(JsonObject root, Collection<ConfigElement<?>> elements, String prefix, BiConsumer<String, String> onMove) {
        int moved = 0;
        for (var element : elements) {
            var path = prefix + element.fieldName();
            var movedFrom = element.getAnnotation(MovedFrom.class);
            if (movedFrom.isPresent() && get(root, path) == null) {
                // The first old place with a value
                for (var oldPath : movedFrom.get().value()) {
                    var old = get(root, oldPath);
                    if (old != null) {
                        set(root, path, old.deepCopy());
                        onMove.accept(oldPath, path);
                        moved++;
                        break;
                    }
                }
            }
            if (element instanceof ConfigElement.PropertyGroup group)
                moved += apply(root, group.getChildren(), path + ".", onMove);
        }
        return moved;
    }

    /**
     * The value at a dotted path, or null if there is none (or something on the way isn't an object).
     */
    static @Nullable JsonElement get(JsonObject root, String path) {
        JsonElement current = root;
        for (var part : path.split("\\.")) {
            if (!(current instanceof JsonObject object))
                return null;
            current = object.get(part);
            if (current == null || current.isJsonNull())
                return null;
        }
        return current;
    }

    /**
     * Puts a value at a dotted path, creating the objects on the way. Does nothing if something on the way is there
     * but isn't an object.
     */
    static void set(JsonObject root, String path, JsonElement value) {
        var parts = path.split("\\.");
        var current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            var next = current.get(parts[i]);
            if (next == null || next.isJsonNull()) {
                var created = new JsonObject();
                current.add(parts[i], created);
                current = created;
            } else if (next instanceof JsonObject object) {
                current = object;
            } else {
                return;
            }
        }
        current.add(parts[parts.length - 1], value);
    }
}
