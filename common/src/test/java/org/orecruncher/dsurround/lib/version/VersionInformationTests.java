package org.orecruncher.dsurround.lib.version;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lang.LanguageFiles;
import org.orecruncher.dsurround.lib.CodecExtensions;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for reading the published version information, choosing the recommendation, and the chat message for it.
 */
public class VersionInformationTests {

    private static final String NOTES = "https://github.com/OreCruncher/DynamicSurroundingsFabric/blob/main/CHANGELOG.md#046";

    // In the shape of versions.json
    private static final String JSON = """
            {
              "releases": {
                "1.21.1": { "0.4.6": "%s", "0.4.5": "%s" },
                "1.21.0": { "0.4.0": "" },
                "1.17.1": { "0.0.2": "Second Alpha" }
              },
              "recommend": {
                "1.21.1": "0.4.6",
                "1.21.0": "0.4.0",
                "1.20.4": "0.3.3",
                "1.17.1": "0.0.2",
                "not a version": "0.0.1"
              }
            }
            """.formatted(NOTES, NOTES);

    @BeforeAll
    static void beforeAll() {
        // VersionResult's chat text uses HoverEvent, which needs the registries
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SemanticVersion v(String text) {
        try {
            return SemanticVersion.parse(text);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }

    private static VersionInformation info() {
        return CodecExtensions.deserialize("test", JSON, VersionInformation.CODEC).orElseThrow();
    }

    // ---- Recommendation --------------------------------------------------------------------------------------

    @Test
    void recommendationWithReleaseNotes() {
        var recommendation = info().getRecommendation(v("1.21.1")).orElseThrow();

        assertEquals(v("0.4.6"), recommendation.version());
        assertEquals(NOTES, recommendation.releaseNotesUrl());
    }

    @Test
    void noRecommendationForThisMinecraftVersion() {
        assertTrue(info().getRecommendation(v("1.19.2")).isEmpty());
    }

    @Test
    void recommendationWithoutAReleasesEntryHasNoLink() {
        var recommendation = info().getRecommendation(v("1.20.4")).orElseThrow();

        assertEquals(v("0.3.3"), recommendation.version());
        assertNull(recommendation.releaseNotesUrl());
    }

    @Test
    void releaseNotesThatArentALinkAreLeftOut() {
        // Regression: the text, or an empty string, was used as the link
        assertNull(info().getRecommendation(v("1.17.1")).orElseThrow().releaseNotesUrl());
        assertNull(info().getRecommendation(v("1.21.0")).orElseThrow().releaseNotesUrl());
    }

    @Test
    void anEntryThatIsntAVersionDoesntLoseTheRest() {
        assertTrue(info().getRecommendation(v("1.21.1")).isPresent());
    }

    @Test
    void onlyWebLinksAreLinks() {
        assertTrue(VersionInformation.isWebLink("https://example.com/notes"));
        assertTrue(VersionInformation.isWebLink("http://example.com"));
        assertFalse(VersionInformation.isWebLink(null));
        assertFalse(VersionInformation.isWebLink(""));
        assertFalse(VersionInformation.isWebLink("Second Alpha"));
        assertFalse(VersionInformation.isWebLink("file:///etc/passwd"));
        assertFalse(VersionInformation.isWebLink("https://"));
    }

    // ---- Chat message ----------------------------------------------------------------------------------------

    private static VersionResult result(String displayName, String releaseNotesLink) {
        return new VersionResult("0.4.6", "dsurround", displayName, "https://curseforge.com", "https://modrinth.com",
                releaseNotesLink, "https://discussions.com", true);
    }

    private static Component chatText(VersionResult result) {
        return result.getChatText(LanguageFiles.lookup("en_us"));
    }

    /**
     * Each link in the message, in order: its URL, hover text and color.
     */
    private static List<String> linksIn(Component component) {
        var links = new ArrayList<String>();
        component.visit((style, text) -> {
            var click = style.getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.OPEN_URL) {
                var hover = style.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT).getString();
                var link = click.getValue() + " | " + hover + " | " + style.getColor().formatValue();
                if (!links.contains(link))
                    links.add(link);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return links;
    }

    @Test
    void chatTextReadsAsBefore() {
        assertEquals("UPDATE Dynamic Surroundings v0.4.6 [Release Notes] [Discussion] [CurseForge] [Modrinth]",
                chatText(result("Dynamic Surroundings", NOTES)).getString());
    }

    @Test
    void chatTextLinksHaveTheirOwnHoverTextAndColor() {
        assertEquals(List.of(
                        NOTES + " | Click to view release notes | #1DACD6",
                        "https://discussions.com | Click to go to discussion forums | #FBCEB1",
                        "https://curseforge.com | Click for download page | #F16436",
                        "https://modrinth.com | Click for download page | #1BD96A"),
                linksIn(chatText(result("Dynamic Surroundings", NOTES))));
    }

    @Test
    void chatTextLeavesOutMissingReleaseNotes() {
        var text = chatText(result("Dynamic Surroundings", null));

        assertEquals("UPDATE Dynamic Surroundings v0.4.6 [Discussion] [CurseForge] [Modrinth]", text.getString());
        assertEquals(3, linksIn(text).size());
    }

    @Test
    void insertedValuesAreNotMarkup() {
        var text = chatText(result("*Dynamic* [Surroundings](https://evil.com) <color:red>", null));

        assertTrue(text.getString().contains("*Dynamic* [Surroundings](https://evil.com) <color:red> v0.4.6"), text.getString());
        assertEquals(3, linksIn(text).size(), "no link from the display name");
    }

    @Test
    void everyLanguageGivesAWorkingMessage() {
        for (var name : LanguageFiles.names()) {
            var text = new VersionResult("0.4.6", "dsurround", "Dynamic Surroundings", "https://curseforge.com",
                    "https://modrinth.com", NOTES, "https://discussions.com", true).getChatText(LanguageFiles.lookup(name));
            var plain = text.getString();

            assertEquals(4, linksIn(text).size(), name + ": " + plain);
            assertFalse(plain.contains("<color") || plain.contains("](") || plain.contains("%") || plain.contains("dsurround."),
                    name + " has markup or a key left in it: " + plain);
        }
    }

    // ---- Failures --------------------------------------------------------------------------------------------

    @Test
    void failureDescriptionLooksThroughWrappers() {
        var cause = new VersionCheckException("unable to fetch https://example.com");

        assertEquals("unable to fetch https://example.com", VersionCheckException.describe(new CompletionException(cause)));
        assertEquals("the check timed out", VersionCheckException.describe(new CompletionException(new TimeoutException())));
        assertEquals("IllegalStateException", VersionCheckException.describe(new IllegalStateException()));
    }
}
