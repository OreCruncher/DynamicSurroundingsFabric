package org.orecruncher.dsurround.effects;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class ModShaderTests {

    /**
     * A ShaderInstance can't be built without a GL context; ModShader only keeps hold of it, so an empty one made
     * without running its constructor will do.
     */
    private static ShaderInstance fakeShaderInstance() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        return (ShaderInstance) unsafe.allocateInstance(ShaderInstance.class);
    }

    private static ModShader shader(RecordingLog log) {
        return new ModShader("test_shader", DefaultVertexFormat.POSITION_TEX_COLOR, "the test effect", "nothing will be drawn", log);
    }

    @Test
    void namedInTheModsNamespace() {
        var shader = shader(new RecordingLog());
        assertEquals(Constants.asId("test_shader"), shader.id());
        assertSame(DefaultVertexFormat.POSITION_TEX_COLOR, shader.format());
    }

    @Test
    void unavailableUntilLoaded() {
        var shader = shader(new RecordingLog());
        assertNull(shader.get());
        assertFalse(shader.isAvailable());
    }

    @Test
    void loadingMakesItAvailable() throws Exception {
        var log = new RecordingLog();
        var shader = shader(log);
        var instance = fakeShaderInstance();
        shader.onLoaded(instance);

        assertSame(instance, shader.get());
        assertSame(instance, shader.supplier().get());
        assertTrue(shader.isAvailable());
        assertTrue(log.at(IModLog.Level.INFO).getFirst().message().contains("dsurround:test_shader"));
    }

    @Test
    void failingToLoadClearsItAndSaysWhatIsLost() throws Exception {
        var log = new RecordingLog();
        var shader = shader(log);
        shader.onLoaded(fakeShaderInstance());
        var error = new RuntimeException("bad shader");
        shader.onFailed(error);

        assertNull(shader.get());
        assertFalse(shader.isAvailable());
        var logged = log.at(IModLog.Level.ERROR).getFirst();
        assertSame(error, logged.throwable());
        assertTrue(logged.message().contains("dsurround:test_shader"));
        assertTrue(logged.message().contains("nothing will be drawn"));
    }

    @Test
    void aDrawingFailureTurnsItOffUntilReloaded() throws Exception {
        var log = new RecordingLog();
        var shader = shader(log);
        shader.onLoaded(fakeShaderInstance());

        assertFalse(shader.run(() -> {
            throw new IllegalStateException("draw failed");
        }, () -> {
        }));
        assertFalse(shader.isAvailable(), "still available after drawing failed");
        assertTrue(log.at(IModLog.Level.ERROR).getFirst().message().contains("the test effect"));

        // Reloading resources hands over the shader again, which turns the effect back on
        shader.onLoaded(fakeShaderInstance());
        assertTrue(shader.isAvailable());
        assertTrue(shader.run(() -> {
        }, () -> {
        }));
    }

    @Test
    void everyRegisteredShaderHasItsFiles() throws Exception {
        var ids = new HashSet<>();
        for (var shader : ModShaders.SHADERS) {
            assertTrue(ids.add(shader.id()), "registered twice: " + shader.id());
            var base = "assets/" + shader.id().getNamespace() + "/shaders/core/" + shader.id().getPath();
            for (var extension : new String[]{".json", ".vsh", ".fsh"})
                assertNotNull(getClass().getClassLoader().getResource(base + extension), "missing " + base + extension);

            // The definition names this shader's own programs
            try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(base + ".json"), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals(shader.id().toString(), json.get("vertex").getAsString());
                assertEquals(shader.id().toString(), json.get("fragment").getAsString());
            }
        }
        assertEquals(3, ids.size());
    }
}
