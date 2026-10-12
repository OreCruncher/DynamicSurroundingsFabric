package org.orecruncher.dsurround.effects;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ModShaderTests {

    // Constants, which a shader's id comes from, needs the game's registries. Run first on its own, this class
    // otherwise fails to load Constants and takes every later test that uses it down too.
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
            assertNotNull(getClass().getClassLoader().getResource(base + ".json"), "missing " + base + ".json");

            // The definition names this shader's own fragment program, and a vertex program that is there (it may
            // share another's)
            try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(base + ".json"), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals(shader.id().toString(), json.get("fragment").getAsString());
                assertNotNull(getClass().getClassLoader().getResource(base + ".fsh"), "missing " + base + ".fsh");
                var vertex = ResourceLocation.parse(json.get("vertex").getAsString());
                var vertexPath = "assets/" + vertex.getNamespace() + "/shaders/core/" + vertex.getPath() + ".vsh";
                assertNotNull(getClass().getClassLoader().getResource(vertexPath), "missing " + vertexPath);
            }
        }
        assertEquals(9, ids.size());
    }

    private String codeOf(String shader) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/dsurround/shaders/core/" + shader)) {
            assertNotNull(in, shader);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(line -> line.replaceAll("//.*", "").strip())
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.joining("\n"));
        }
    }

    @Test
    void theSoftParticleShadersDifferOnlyInWritingDepth() throws Exception {
        // Regression: the depth write was chosen by a uniform, so the shader wrote depth on every setting, and early
        // depth testing was lost even where nothing was written. The Fabulous one is apart; it must not drift
        var plain = codeOf("soft_particle.fsh");
        var sceneDepth = codeOf("soft_particle_scene_depth.fsh");
        assertFalse(plain.contains("gl_FragDepth"), "soft_particle.fsh writes depth");
        assertTrue(sceneDepth.contains("gl_FragDepth = sceneDepth;"));
        assertEquals(plain, sceneDepth.replace("gl_FragDepth = sceneDepth;\n", ""));
    }
}
