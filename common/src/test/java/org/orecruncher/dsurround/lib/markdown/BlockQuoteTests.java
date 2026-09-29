package org.orecruncher.dsurround.lib.markdown;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockQuoteTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testBlockQuotes() {
        String markdown = "> This is a single line block quote.";

        Options options = Options.UNIFORM;

        String jsonOutput = MarkdownParser.parseToComponentJson(markdown, options);
        JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

        assertNotNull(root);
        assertTrue(root.has("extra"));
        var siblings = root.getAsJsonArray("extra");
        assertEquals(1, siblings.size());
        assertEquals("│ This is a single line block quote.", siblings.get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    public void testMultilineBlockquote() {
        String markdown = "> First quote line\n> Second quote line";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown, Options.DEFAULT);

        // Assert that the generated JSON contains the correct structure, prefix, and styles
        assertNotNull(jsonOutput, "JSON output should not be null");

        // Check that quote prefixes and lines are present
        assertTrue(jsonOutput.contains(Options.DEFAULT.quoteStyle() + "First quote line"), "Should contain the first quoted line with prefix");
        assertTrue(jsonOutput.contains(Options.DEFAULT.quoteStyle() + "Second quote line"), "Should contain the second quoted line with prefix");

        // Check that blockquote styling (gray color and italic) is correctly applied
        assertTrue(jsonOutput.contains("\"color\":\"#AAAAAA\""), "Should apply default blockquote color");
        assertTrue(jsonOutput.contains("\"italic\":true"), "Should apply italic styling to blockquotes");
    }

    @Test
    void testBlockQuoteWithEmbeddedFormatting() {
        String markdown = "> Quote with **bold text** inside.";

        Options options = Options.DEFAULT;
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown, options);
        JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

        // Recursively search the entire JSON component tree for a bold element with our text
        boolean foundBold = findElementRecursive(root, "bold", "bold text");
        assertTrue(foundBold, "Expected embedded bold element with text 'bold text' to be parsed inside block quote. Full JSON output: " + jsonOutput);
    }

    // Helper method to recursively search Minecraft JSON text component trees (accounting for parent style inheritance)
    private boolean findElementRecursive(com.google.gson.JsonElement element, String targetKey, String expectedTextValue) {
        if (element == null || !element.isJsonObject()) return false;
        JsonObject obj = element.getAsJsonObject();

        boolean isBoldContainer = obj.has(targetKey) && obj.get(targetKey).getAsBoolean();

        // Check if this object's text contains the target value and it has the bold attribute
        if (isBoldContainer && obj.has("text") && obj.get("text").getAsString().contains(expectedTextValue)) {
            return true;
        }

        // Check if the bold attribute is on this container, and the text is inside its 'extra' children
        if (isBoldContainer && obj.has("extra") && obj.get("extra").isJsonArray()) {
            for (var child : obj.getAsJsonArray("extra")) {
                if (child.isJsonObject() && child.getAsJsonObject().has("text")) {
                    if (child.getAsJsonObject().get("text").getAsString().contains(expectedTextValue)) {
                        return true;
                    }
                }
            }
        }

        // Recursively check 'extra' array for nested structures
        if (obj.has("extra") && obj.get("extra").isJsonArray()) {
            for (var child : obj.getAsJsonArray("extra")) {
                if (findElementRecursive(child, targetKey, expectedTextValue)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Test
    public void testHeaderInsideBlockquote() {
        String markdown = "> # Header Inside Quote";
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown);

        // Ensure conversion completes successfully without hanging (infinite loop protection)
        assertNotNull(jsonOutput, "JSON output should not be null");

        // Validate that the quote prefix and header text are properly rendered
        assertTrue(jsonOutput.contains(Options.DEFAULT.quoteStyle()), "Should contain the configured quote prefix");
        assertTrue(jsonOutput.contains("Header Inside Quote"), "Should contain the header text");

        // Validate that both blockquote and header styling are correctly applied
        assertFalse(jsonOutput.contains("\"italic\":true"), "Should not italic formatting from the blockquote");
        assertTrue(jsonOutput.contains("\"bold\":true"), "Should apply bold formatting for the header");
        assertTrue(jsonOutput.contains("\"color\":\"%s\"".formatted(Options.DEFAULT.quoteColor())), "Should apply the header color");
    }
}
