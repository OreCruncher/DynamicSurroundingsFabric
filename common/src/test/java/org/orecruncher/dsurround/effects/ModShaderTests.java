package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class ModShaderTests {

    /**
     * Pipelines can't be compiled without a GPU device, so these tests hand ModShader the result of compiling one.
     */
    private static ModShader shader(RecordingLog log) {
        var pipeline = RenderPipeline.builder()
                .withLocation(Constants.asId("pipeline/test_shader"))
                .withVertexShader(Constants.asId("core/test_shader"))
                .withFragmentShader(Constants.asId("core/test_shader"))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build();
        return new ModShader(pipeline, "the test effect", "nothing will be drawn", log);
    }

    @Test
    void namedInTheModsNamespace() {
        var shader = shader(new RecordingLog());
        assertEquals(Constants.asId("pipeline/test_shader"), shader.id());
    }

    @Test
    void unavailableUntilCompiled() {
        var shader = shader(new RecordingLog());
        assertFalse(shader.isAvailable());
    }

    @Test
    void compilingMakesItAvailable() {
        var log = new RecordingLog();
        var shader = shader(log);
        shader.onCompiled(true);

        assertTrue(shader.isAvailable());
        assertTrue(log.at(IModLog.Level.INFO).getFirst().message().contains("dsurround:pipeline/test_shader"));
    }

    @Test
    void notCompilingLeavesItUnavailableAndSaysWhatIsLost() {
        var log = new RecordingLog();
        var shader = shader(log);
        shader.onCompiled(false);

        assertFalse(shader.isAvailable());
        var logged = log.at(IModLog.Level.ERROR).getFirst();
        assertTrue(logged.message().contains("dsurround:pipeline/test_shader"));
        assertTrue(logged.message().contains("nothing will be drawn"));
    }

    @Test
    void failingToCompileOnReloadTurnsItOff() {
        var log = new RecordingLog();
        var shader = shader(log);
        shader.onCompiled(true);
        var error = new RuntimeException("bad shader");
        shader.onFailed(error);

        assertFalse(shader.isAvailable());
        var logged = log.at(IModLog.Level.ERROR).getFirst();
        assertSame(error, logged.throwable());
        assertTrue(logged.message().contains("nothing will be drawn"));
    }

    @Test
    void aDrawingFailureTurnsItOffUntilReloaded() {
        var log = new RecordingLog();
        var shader = shader(log);
        shader.onCompiled(true);

        assertFalse(shader.run(() -> {
            throw new IllegalStateException("draw failed");
        }, () -> {
        }));
        assertFalse(shader.isAvailable(), "still available after drawing failed");
        assertTrue(log.at(IModLog.Level.ERROR).getFirst().message().contains("the test effect"));

        // Reloading resources compiles the pipeline again, which turns the effect back on
        shader.onCompiled(true);
        assertTrue(shader.isAvailable());
        assertTrue(shader.run(() -> {
        }, () -> {
        }));
    }

    @Test
    void everyShaderHasItsFiles() {
        var ids = new HashSet<Identifier>();
        for (var shader : ModShaders.SHADERS) {
            assertTrue(ids.add(shader.id()), "listed twice: " + shader.id());
            var pipeline = shader.pipeline();
            assertResource(pipeline.getVertexShader(), ".vsh");
            assertResource(pipeline.getFragmentShader(), ".fsh");
        }
        assertEquals(3, ids.size());
    }

    private void assertResource(Identifier shaderId, String extension) {
        var path = "assets/" + shaderId.getNamespace() + "/shaders/" + shaderId.getPath() + extension;
        assertNotNull(getClass().getClassLoader().getResource(path), "missing " + path);
    }
}
