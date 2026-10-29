package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class ModShaderTests {

    // Constants, which a shader's id comes from, needs the game's registries. Run first on its own, this class
    // otherwise fails to load Constants and takes every later test that uses it down too.
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Pipelines can't be compiled without a GPU device, so these tests hand ModShader the result of compiling one.
     */
    private static ModShader shader(RecordingLog log) {
        var pipeline = RenderPipeline.builder()
                .withLocation(Constants.asId("pipeline/test_shader"))
                .withVertexShader(Constants.asId("core/test_shader"))
                .withFragmentShader(Constants.asId("core/test_shader"))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
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
        assertEquals(ModShaders.SHADERS.size(), ids.size());
    }

    @Test
    void thoseForFabulousGraphicsWriteDepth() {
        // With Fabulous graphics a target is laid over the scene by its depth; a pipeline that is meant to write it
        // there and doesn't leaves its target as far as can be, and the scene is laid over everything it drew: rain
        // only against the sky, damp spots not at all
        int checked = 0;
        for (var shader : ModShaders.SHADERS) {
            var path = shader.id().getPath();
            if (!path.endsWith("_depth_write") && !path.endsWith("_depth"))
                continue;
            assertTrue(shader.pipeline().isWriteDepth(), path + " doesn't write depth");
            checked++;
        }
        assertTrue(checked >= 7, "found only " + checked);
    }

    @Test
    void theOthersWriteNoDepth() {
        // A builder's default here is to test and write depth; the one of a pair drawn without Fabulous graphics, or a
        // soft layer's color pass after its depth (_fabulous), that wrote it would hide what is drawn behind it after
        int checked = 0;
        var paths = ModShaders.SHADERS.stream().map(shader -> shader.id().getPath()).toList();
        for (var shader : ModShaders.SHADERS) {
            var path = shader.id().getPath();
            if (!path.endsWith("_fabulous") && !paths.contains(path + "_depth_write"))
                continue;
            assertFalse(shader.pipeline().isWriteDepth(), path + " writes depth");
            checked++;
        }
        assertTrue(checked >= 9, "found only " + checked);
    }

    @Test
    void noShaderReturnsWithoutAValue() throws Exception {
        // Veil re-reads every core shader to add its buffers, and its parser (glsl-processor) fails on "return;",
        // logging an error with a stack trace at every resource reload
        var anchor = getClass().getClassLoader().getResource("assets/dsurround/shaders/core/aurora.fsh");
        assertNotNull(anchor);
        var folder = Path.of(anchor.toURI()).getParent().getParent();
        List<Path> programs;
        try (var files = Files.walk(folder)) {
            programs = files.filter(file -> file.toString().matches(".*\\.(vsh|fsh|glsl)")).toList();
        }
        assertFalse(programs.isEmpty());
        var bareReturn = Pattern.compile("\\breturn\\s*;");
        for (var program : programs) {
            var code = Files.readString(program);
            assertFalse(bareReturn.matcher(code).find(), program.getFileName() + " has a return without a value");
        }
    }

    private void assertResource(Identifier shaderId, String extension) {
        var path = "assets/" + shaderId.getNamespace() + "/shaders/" + shaderId.getPath() + extension;
        assertNotNull(getClass().getClassLoader().getResource(path), "missing " + path);
    }
}
