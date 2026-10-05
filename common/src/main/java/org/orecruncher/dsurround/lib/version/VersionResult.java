package org.orecruncher.dsurround.lib.version;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.text.Localization;
import org.orecruncher.dsurround.lib.markdown.MarkdownParser;
import org.orecruncher.dsurround.lib.markdown.Options;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.function.Function;

/**
 * The outcome of a version check, and the chat message describing it.
 * <p>
 * The message is markdown from the language file ({@code <modId>.newversion.message}), with the version details
 * inserted, so translators control the wording, link text, hover text (link titles) and colors. Inserted values are
 * escaped, so nothing in them is taken as markup.
 *
 * @param releaseNotesLink link to the release notes, or null if there isn't one; the message then leaves the link out
 */
public record VersionResult(
        String version,
        String modId,
        String displayName,
        String downloadLocation,
        String downloadLocationModrinth,
        @Nullable String releaseNotesLink,
        String discussionsLink,
        boolean updateAvailable) {

    // Each link has its own color, set with <color> in the message
    private static final Options CHAT_OPTIONS = Options.builder().colorOverridesLink(true).build();

    public Component getChatText() {
        return this.getChatText(Localization::load);
    }

    /**
     * The chat message, with text from {@code language}, which maps a translation key to its text.
     */
    Component getChatText(Function<String, String> language) {
        var status = language.apply(this.modId + ".newversion." + (this.updateAvailable ? "update" : "current"));

        // Markdown has no conditionals, so the release notes link is a piece of its own, left out when there's no link
        var releaseNotes = this.releaseNotesLink == null
                ? ""
                : format(language, this.modId + ".newversion.releasenoteslink", MarkdownParser.escape(this.releaseNotesLink));

        var markdown = format(language, this.modId + ".newversion.message",
                MarkdownParser.escape(status),
                MarkdownParser.escape(this.displayName),
                MarkdownParser.escape(this.version),
                releaseNotes,
                MarkdownParser.escape(this.discussionsLink),
                MarkdownParser.escape(this.downloadLocation),
                MarkdownParser.escape(this.downloadLocationModrinth));

        return MarkdownParser.markdownToComponent(markdown, CHAT_OPTIONS).orElseGet(Component::empty);
    }

    /**
     * The text for {@code key} with {@code args} inserted, as {@link String#format} does. A translation with a
     * broken placeholder is used as it is, rather than losing the message.
     */
    private static String format(Function<String, String> language, String key, Object... args) {
        var template = language.apply(key);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException e) {
            Library.LOGGER.warn("Translation '%s' has a placeholder that isn't valid: %s", key, e.getMessage());
            return template;
        }
    }
}
