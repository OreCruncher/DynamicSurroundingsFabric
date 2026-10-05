package org.orecruncher.dsurround.lib.platform;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ModLinksTests {

    @Test
    void everyLinkIsFilledInByTheBuild() {
        // A link left as "${...}" means its gradle property is missing from the expansion in common/build.gradle
        for (var name : new String[]{"homepage", "issues", "update", "discussions", "curseforge", "modrinth"}) {
            var link = ModInformation.link(name);
            assertTrue(link.startsWith("https://"), name + " is '" + link + "'");
            assertFalse(link.contains("${"), name + " wasn't filled in: " + link);
        }
    }

    @Test
    void theUpdateLinkIsAUrl() {
        assertTrue(ModInformation.toUrl(ModInformation.link("update")).isPresent());
    }

    @Test
    void anUnknownLinkIsEmpty() {
        assertEquals("", ModInformation.link("nonexistent"));
    }

    @Test
    void badOrMissingLinksGiveNoUrl() {
        assertTrue(ModInformation.toUrl("").isEmpty());
        assertTrue(ModInformation.toUrl("not a url").isEmpty());
        assertTrue(ModInformation.toUrl("${mod_update_url}").isEmpty());
    }

    @Test
    void aMissingLinksFileMeansNoLinksRatherThanAFailure() {
        assertTrue(ModInformation.loadLinks(null).isEmpty());
    }

    @Test
    void readsTheLinksFile() {
        var text = "# comment\nupdate=https://example.com/versions.json\n";
        var links = ModInformation.loadLinks(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals("https://example.com/versions.json", links.getProperty("update"));
    }
}
