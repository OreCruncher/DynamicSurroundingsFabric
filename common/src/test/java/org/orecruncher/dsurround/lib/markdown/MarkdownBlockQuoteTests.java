package org.orecruncher.dsurround.lib.markdown;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarkdownBlockQuoteTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testBlockQuotes() {
        String markdown = "> This is a single line block quote.";

        ParserOptions options = ParserOptions.UNIFORM;

        String jsonOutput = MarkdownParser.parseToComponentJson(markdown, options);
        JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

        assertNotNull(root);
        assertTrue(root.has("extra"));
        var siblings = root.getAsJsonArray("extra");
        assertEquals(3, siblings.size());

        // Validate prefix element
        JsonObject prefixElement = siblings.get(0).getAsJsonObject();
        assertEquals("│ ", prefixElement.get("text").getAsString());
        assertEquals("gray", prefixElement.get("color").getAsString());
        assertEquals("minecraft:uniform", prefixElement.get("font").getAsString());

        // Validate quote text container element
        JsonObject quoteElement = siblings.get(1).getAsJsonObject();
        assertEquals("", quoteElement.get("text").getAsString());
        assertEquals("gray", quoteElement.get("color").getAsString());
        assertEquals("minecraft:uniform", quoteElement.get("font").getAsString());

        assertTrue(quoteElement.has("extra"));
        var quoteSiblings = quoteElement.getAsJsonArray("extra");
        assertEquals(1, quoteSiblings.size());
        assertEquals("This is a single line block quote.", quoteSiblings.get(0).getAsJsonObject().get("text").getAsString());

        // Validate terminating block break
        JsonObject breakElement = siblings.get(2).getAsJsonObject();
        assertEquals("\n\n", breakElement.get("text").getAsString());
    }

    @Test
    void testConsecutiveBlockQuotes() {
        String markdown = "> First quote line\n> Second quote line";

        ParserOptions options = ParserOptions.DEFAULT;
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown, options);
        JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

        var siblings = root.getAsJsonArray("extra");
        assertEquals(6, siblings.size());

        // The break after the first quote line should be a single '\n'
        JsonObject firstBreak = siblings.get(2).getAsJsonObject();
        assertEquals("\n", firstBreak.get("text").getAsString());

        // The break after the final quote line should be '\n\n'
        JsonObject finalBreak = siblings.get(5).getAsJsonObject();
        assertEquals("\n\n", finalBreak.get("text").getAsString());
    }

    @Test
    void testBlockQuoteWithEmbeddedFormatting() {
        String markdown = "> Quote with **bold text** inside.";

        ParserOptions options = ParserOptions.DEFAULT;
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown, options);
        JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

        // Recursively search the entire JSON component tree for a bold element with our text
        boolean foundBold = findElementRecursive(root, "bold", "bold text");
        assertTrue(foundBold, "Expected embedded bold element with text 'bold text' to be parsed inside block quote. Full JSON output: " + jsonOutput);
    }

    // Helper method to recursively search Minecraft JSON text component trees
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
    void testBlockQuoteWithHeader() {
        String markdown = "> # Header Inside Quote";

        ParserOptions options = ParserOptions.DEFAULT;
        String jsonOutput = MarkdownParser.parseToComponentJson(markdown, options);
        JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

        var siblings = root.getAsJsonArray("extra");
        assertEquals(3, siblings.size());

        // 1. Prefix
        assertEquals("│ ", siblings.get(0).getAsJsonObject().get("text").getAsString());

        // 2. Header Object inside quote
        JsonObject headerObj = siblings.get(1).getAsJsonObject();
        assertTrue(headerObj.get("bold").getAsBoolean());
        assertEquals(options.defaultHeadingColor(), headerObj.get("color").getAsString());

        var headerSiblings = headerObj.getAsJsonArray("extra");
        assertEquals("Header Inside Quote", headerSiblings.get(0).getAsJsonObject().get("text").getAsString());

        // 3. Block Break
        assertEquals("\n\n", siblings.get(2).getAsJsonObject().get("text").getAsString());
    }
}
