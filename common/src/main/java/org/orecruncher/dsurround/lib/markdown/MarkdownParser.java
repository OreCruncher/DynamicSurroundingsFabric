package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.orecruncher.dsurround.lib.CodecExtensions;

import java.util.List;
import java.util.Optional;

public final class MarkdownParser {

    MarkdownParser() {}

    /**
     * Converts the mark-down document into a Component representation for rendering
     */
    public static Optional<Component> markdownToComponent(String markdown) {
        return markdownToComponent(markdown, Options.DEFAULT);
    }

    public static Optional<Component> markdownToComponent(String markdown, Options parserOptions) {
        var json = parseToComponentJson(markdown, parserOptions);
        return CodecExtensions.deserialize(json, ComponentSerialization.CODEC);
    }

    static String parseToComponentJson(String markdown) {
        return parseToComponentJson(markdown, Options.DEFAULT);
    }

    /**
     * Parses a Markdown string into a Minecraft JSON Text Component string using custom options.
     */
    static String parseToComponentJson(String markdown, Options options) {

        // On Windows newlines are a bit different so we need to clean them up
        markdown = markdown.replace("\r\n", "\n").replace('\r', '\n');

        Scanner scanner = new Scanner(markdown);
        List<Token> rawTokens = scanner.scan();

        Tokenizer tokenizer = new Tokenizer(rawTokens, options);
        ComponentNode root = tokenizer.tokenize();

        ComponentNode optimized = Optimizer.optimize(root);
        return JsonExporter.serialize(optimized, options);
    }
}
