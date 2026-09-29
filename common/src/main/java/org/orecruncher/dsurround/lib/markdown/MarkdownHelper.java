package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.orecruncher.dsurround.lib.CodecExtensions;

import java.util.Optional;

public final class MarkdownHelper {

    MarkdownHelper() {}

    /**
     * Converts the mark-down document into a Component representation for rendering
     */
    public static Optional<Component> markdownToComponent(String markdown) {
        return markdownToComponent(markdown, ParserOptions.DEFAULT);
    }

    public static Optional<Component> markdownToComponent(String markdown, ParserOptions parserOptions) {
        var json = MarkdownParser.parseToComponentJson(markdown, parserOptions);
        return CodecExtensions.deserialize(json, ComponentSerialization.CODEC);
    }
}
