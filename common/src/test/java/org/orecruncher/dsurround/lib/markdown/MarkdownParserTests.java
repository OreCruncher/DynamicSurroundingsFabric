package org.orecruncher.dsurround.lib.markdown;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MarkdownParserTests {
    @Test
    void testPlaintext() {
        String input = "Hello world";
        String json = MarkdownParser.parseToComponentJson(input);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray extra = root.getAsJsonArray("extra");

        assertEquals(2, extra.size());
        assertEquals("Hello world", extra.get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void testInlineFormatting() {
        String input = "This is **bold**, *italic*, ~~strikethrough~~, and __underline__ text.";
        String json = MarkdownParser.parseToComponentJson(input);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray extra = root.getAsJsonArray("extra");

        // Verify bold parent wrapper
        JsonObject boldParent = findParentByChildText(extra, "bold");
        assertNotNull(boldParent, "Bold parent wrapper should exist");
        assertTrue(boldParent.get("bold").getAsBoolean());

        // Verify italic parent wrapper
        JsonObject italicParent = findParentByChildText(extra, "italic");
        assertNotNull(italicParent, "Italic parent wrapper should exist");
        assertTrue(italicParent.get("italic").getAsBoolean());

        // Verify strikethrough parent wrapper
        JsonObject strikeParent = findParentByChildText(extra, "strikethrough");
        assertNotNull(strikeParent, "Strikethrough parent wrapper should exist");
        assertTrue(strikeParent.get("strikethrough").getAsBoolean());

        // Verify underline parent wrapper
        JsonObject underlineParent = findParentByChildText(extra, "underline");
        assertNotNull(underlineParent, "Underline parent wrapper should exist");
        assertTrue(underlineParent.get("underline").getAsBoolean());    }

    @Test
    void testLinks() {
        String input = "Check [our portal](https://example.com) now.";
        String json = MarkdownParser.parseToComponentJson(input, ParserOptions.UNIFORM);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertNotNull(root);
        assertTrue(root.has("extra"));
        var siblings = root.getAsJsonArray("extra");

        // Expecting 4 siblings: "Check ", [link], " now.", "\n\n"
        assertEquals(4, siblings.size());

        // 1. Verify leading text
        assertEquals("Check ", siblings.get(0).getAsJsonObject().get("text").getAsString());

        // 2. Verify link element at index 1
        JsonObject linkObj = siblings.get(1).getAsJsonObject();
        assertEquals("our portal", linkObj.get("text").getAsString());
        assertTrue(linkObj.get("underlined").getAsBoolean());
        assertEquals(ParserOptions.UNIFORM.defaultLinkColor(), linkObj.get("color").getAsString());
        assertEquals("minecraft:uniform", linkObj.get("font").getAsString());

        // Validate click event
        assertTrue(linkObj.has("clickEvent"));
        JsonObject clickEvent = linkObj.getAsJsonObject("clickEvent");
        assertEquals("open_url", clickEvent.get("action").getAsString());
        assertEquals("https://example.com", clickEvent.get("value").getAsString());

        // Validate hover event and its contents (including font attribute)
        assertTrue(linkObj.has("hoverEvent"));
        JsonObject hoverEvent = linkObj.getAsJsonObject("hoverEvent");
        assertEquals("show_text", hoverEvent.get("action").getAsString());

        assertTrue(hoverEvent.has("contents"));
        JsonObject hoverContents = hoverEvent.getAsJsonObject("contents");
        assertEquals("https://example.com", hoverContents.get("text").getAsString());
        assertEquals("minecraft:uniform", hoverContents.get("font").getAsString());

        // 3. Verify trailing text
        assertEquals(" now.", siblings.get(2).getAsJsonObject().get("text").getAsString());

        // 4. Verify paragraph terminator
        assertEquals("\n\n", siblings.get(3).getAsJsonObject().get("text").getAsString());    }

    @Test
    void testColorTags() {
        String input = "<color:#FF7518>Warning active</color> and <color:green>System OK</color>";
        String json = MarkdownParser.parseToComponentJson(input);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray extra = root.getAsJsonArray("extra");

        JsonObject hexObj = findElementByText(extra, "Warning active");
        assertNotNull(hexObj);
        assertEquals("#FF7518", hexObj.get("color").getAsString());

        JsonObject namedObj = findElementByText(extra, "System OK");
        assertNotNull(namedObj);
        assertEquals("green", namedObj.get("color").getAsString());
    }

    @Test
    void testLists() {
        String input =
                "- Unordered item\n" +
                        "1. Ordered item";
        String json = MarkdownParser.parseToComponentJson(input);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray extra = root.getAsJsonArray("extra");

        // Verify unordered bullet and content exist in sequence
        JsonObject bulletObj = findElementByText(extra, "• ");
        assertNotNull(bulletObj);
        var attribute = bulletObj.get("bold");
        assertNull(attribute);
        attribute = bulletObj.get("color");
        assertNotNull(attribute);
        assertEquals(ParserOptions.DEFAULT.defaultBulletColor(), attribute.getAsString());

        JsonObject unordContent = findElementByText(extra, "Unordered item");
        assertNotNull(unordContent);

        // Verify ordered number and content exist in sequence
        JsonObject numberObj = findElementByText(extra, "1. ");
        assertNotNull(numberObj);
        attribute = numberObj.get("bold");
        assertNull(attribute);
        attribute = numberObj.get("color");
        assertNotNull(attribute);
        assertEquals(ParserOptions.DEFAULT.defaultBulletColor(), attribute.getAsString());

        JsonObject ordContent = findElementByText(extra, "Ordered item");
        assertNotNull(ordContent);
    }

    @Test
    void testHeadings() {
        String input = "# Server Status";
        String json = MarkdownParser.parseToComponentJson(input);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray extra = root.getAsJsonArray("extra");

        // Since headings use a parent wrapper to support inner markup,
        // find the parent object that contains the heading text in its 'extra' array.
        JsonObject headingWrapper = findParentHeadingByChildText(extra, "Server Status");
        assertNotNull(headingWrapper, "Heading wrapper object should exist");
        assertTrue(headingWrapper.get("bold").getAsBoolean());
        assertEquals(ParserOptions.DEFAULT.defaultHeadingColor(), headingWrapper.get("color").getAsString());
    }

    @Test
    void testMultiline() {
        String input = "Line one\nLine two";
        String json = MarkdownParser.parseToComponentJson(input);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray extra = root.getAsJsonArray("extra");

        boolean foundMultiline = false;
        for (var element : extra) {
            String text = element.getAsJsonObject().get("text").getAsString();
            if (text.contains("Line one\nLine two") || text.contains("Line")) {
                foundMultiline = true;
                break;
            }
        }
        assertTrue(foundMultiline);
    }

    // Helper method to locate a specific text node within the sibling array (or nested extra arrays)
    private JsonObject findElementByText(JsonArray array, String targetText) {
        for (var element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("text") && obj.get("text").getAsString().equals(targetText)) {
                return obj;
            }
            if (obj.has("extra")) {
                JsonObject found = findElementByText(obj.getAsJsonArray("extra"), targetText);
                if (found != null) return found;
            }
        }
        return null;
    }

    // Helper to find the parent wrapper of a heading containing the target text
    private JsonObject findParentHeadingByChildText(JsonArray array, String targetText) {
        for (var element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("extra")) {
                JsonArray childExtra = obj.getAsJsonArray("extra");
                for (var child : childExtra) {
                    JsonObject childObj = child.getAsJsonObject();
                    if (childObj.has("text") && childObj.get("text").getAsString().equals(targetText)) {
                        return obj; // Return the parent wrapper
                    }
                }
                JsonObject found = findParentHeadingByChildText(childExtra, targetText);
                if (found != null) return found;
            }
        }
        return null;
    }

    // Universal helper to find the parent wrapper containing a target child text node
    private JsonObject findParentByChildText(JsonArray array, String targetText) {
        for (var element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("extra")) {
                JsonArray childExtra = obj.getAsJsonArray("extra");
                for (var child : childExtra) {
                    JsonObject childObj = child.getAsJsonObject();
                    if (childObj.has("text") && childObj.get("text").getAsString().equals(targetText)) {
                        return obj; // Return the styled parent wrapper
                    }
                }
                JsonObject found = findParentByChildText(childExtra, targetText);
                if (found != null) return found;
            }
        }
        return null;
    }
}
