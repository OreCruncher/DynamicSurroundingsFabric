package org.orecruncher.dsurround.mixinutils;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class DSurroundMixinPluginTests {

    private static final String CLOTH_MIXIN = "org.orecruncher.dsurround.mixins.core.MixinClothAbstractConfigEntry";

    @Test
    void appliesOrdinaryMixinsRegardless() {
        assertTrue(DSurroundMixinPlugin.shouldApply("org.orecruncher.dsurround.mixins.core.MixinLevelRenderer", name -> false));
    }

    @Test
    void appliesTheClothMixinOnlyWithCloth() {
        assertTrue(DSurroundMixinPlugin.shouldApply(CLOTH_MIXIN, name -> true));
        assertFalse(DSurroundMixinPlugin.shouldApply(CLOTH_MIXIN, name -> false));
    }

    @Test
    void asksAboutTheMixinsTarget() {
        var asked = new String[1];
        DSurroundMixinPlugin.shouldApply(CLOTH_MIXIN, name -> {
            asked[0] = name;
            return true;
        });
        assertEquals("me.shedaniel.clothconfig2.api.AbstractConfigEntry", asked[0]);
    }

    @Test
    void optionalMixinsAreInTheMixinConfig() throws Exception {
        // Guards against a mixin being renamed or moved, which would quietly stop the plugin recognising it
        var stream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dsurround.mixins.json"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var pkg = json.get("package").getAsString();
            var listed = new HashSet<String>();
            for (var section : new String[]{"mixins", "client", "server"})
                if (json.has(section))
                    json.getAsJsonArray(section).forEach(e -> listed.add(pkg + "." + e.getAsString()));

            for (var mixin : DSurroundMixinPlugin.OPTIONAL.keySet())
                assertTrue(listed.contains(mixin), mixin + " isn't in dsurround.mixins.json");

            // And the config uses the plugin
            assertEquals(DSurroundMixinPlugin.class.getName(), json.get("plugin").getAsString());
        }
    }

    @Test
    void theTargetsExistInTheBuild() {
        // Cloth Config is on the build's class path, so its class is there; a typo in the name would fail this
        for (var target : DSurroundMixinPlugin.OPTIONAL.values())
            assertDoesNotThrow(() -> Class.forName(target, false, getClass().getClassLoader()), target);
    }
}
