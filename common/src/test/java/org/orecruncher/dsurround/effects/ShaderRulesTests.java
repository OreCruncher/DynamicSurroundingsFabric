// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.aurora.CinematicAuroraFrame;
import org.orecruncher.dsurround.effects.aurora.RealisticAuroraFrame;
import org.orecruncher.dsurround.effects.groundfog.GroundFogFrame;
import org.orecruncher.dsurround.effects.halo.HaloFrame;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;
import org.orecruncher.dsurround.effects.rainbow.RainbowFrame;
import org.orecruncher.dsurround.testing.ShaderSources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Holds every shader and pipeline to the rules AGENTS.md gives for them, each learned from a crash or an effect that
 * vanished on some driver (Vulkan, AMD): every stage compiled to SPIR-V, values passed between stages matched by
 * location, std140 blocks written as the Java packs them.
 */
public class ShaderRulesTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // An input or output between stages, or a vertex attribute: its qualifiers, type and name
    private static final Pattern STAGE_VALUE = Pattern.compile(
            "^\\s*(layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?((?:flat|smooth|noperspective)\\s+)?(in|out)\\s+(\\w+)\\s+(\\w+)\\s*(\\[[^]]*])?\\s*;",
            Pattern.MULTILINE);

    private record StageValue(int location, String interpolation, String type, String name) {
    }

    private static List<StageValue> values(String program, String direction, String where) {
        var found = new ArrayList<StageValue>();
        var match = STAGE_VALUE.matcher(program);
        while (match.find()) {
            if (!match.group(4).equals(direction))
                continue;
            var name = match.group(6);
            assertNotNull(match.group(2), where + ": " + direction + " " + name + " has no layout(location = N)");
            assertNull(match.group(7), where + ": " + direction + " " + name + " is an array, which Vulkan can't match between stages");
            assertFalse(match.group(5).startsWith("mat"), where + ": " + direction + " " + name + " is a matrix, which takes several locations");
            var interpolation = match.group(3) == null ? "" : match.group(3).trim();
            found.add(new StageValue(Integer.parseInt(match.group(2)), interpolation, match.group(5), name));
        }
        return found;
    }

    private static Set<String> defined(RenderPipeline pipeline) {
        var defines = pipeline.getShaderDefines();
        var names = new HashSet<>(defines.flags());
        names.addAll(defines.values().keySet());
        return names;
    }

    // The mod's own pipelines, each with both shaders its own (a vanilla shader is vanilla's to keep right)
    private static List<RenderPipeline> ourPipelines() {
        var pipelines = new ArrayList<RenderPipeline>();
        for (var shader : ModShaders.SHADERS) {
            var shaders = shader.pipeline().getShaders();
            if (shaders.values().stream().allMatch(id -> id.getNamespace().equals("dsurround")))
                pipelines.add(shader.pipeline());
        }
        return pipelines;
    }

    @Test
    void eachFragmentShaderTakesWhatItsVertexShaderHandsIt() throws IOException {
        // Vulkan matches them by location: an output the fragment shader doesn't declare shifts every later one, and a
        // matrix's four locations overlapped the next value (AMD crashed creating the pipeline)
        var pipelines = ourPipelines();
        assertTrue(pipelines.size() > 20, "the pipelines checked: " + pipelines.size());
        for (var pipeline : pipelines) {
            var where = pipeline.getLocation().toString();
            var defined = defined(pipeline);
            var vertex = ShaderSources.program(pipeline.getShaders().get(ShaderType.VERTEX), ".vsh", defined);
            var fragment = ShaderSources.program(pipeline.getShaders().get(ShaderType.FRAGMENT), ".fsh", defined);
            var handed = values(vertex, "out", where + " (vertex)");
            var taken = values(fragment, "in", where + " (fragment)");
            assertEquals(handed.size(), taken.size(), where + ": the vertex shader hands " + handed + ", the fragment shader takes " + taken);
            for (int i = 0; i < handed.size(); i++) {
                var out = handed.get(i);
                var in = taken.get(i);
                assertEquals(out.location(), in.location(), where + ": " + out + " against " + in);
                assertEquals(out.type(), in.type(), where + ": " + out + " against " + in);
                assertEquals(out.interpolation(), in.interpolation(), where + ": " + out + " against " + in);
            }
            // Locations counted over those declared, in order
            for (int i = 0; i < handed.size(); i++)
                assertEquals(i, handed.get(i).location(), where + ": locations out of order " + handed);
        }
    }

    @Test
    void aVertexShaderReadsItsFormatInOrder() throws IOException {
        for (var pipeline : ourPipelines()) {
            var where = pipeline.getLocation().toString();
            var vertex = ShaderSources.program(pipeline.getShaders().get(ShaderType.VERTEX), ".vsh", defined(pipeline));
            var attributes = values(vertex, "in", where);
            var bindings = pipeline.getVertexFormatBindings();
            if (bindings.isEmpty()) {
                assertEquals(List.of(), attributes, where + " has no vertex buffer, so no attributes");
                continue;
            }
            var format = bindings.getFirst();
            for (var attribute : attributes) {
                assertTrue(format.contains(attribute.name()), where + ": " + attribute.name() + " isn't in its vertex format");
                assertEquals(format.getElements().indexOf(format.getElement(attribute.name())), attribute.location(),
                        where + ": " + attribute.name() + " isn't at its place in the vertex format");
            }
        }
    }

    @Test
    void everyShaderIsWrittenForSpirV() throws Exception {
        var anchor = getClass().getClassLoader().getResource("assets/dsurround/shaders/core/aurora.fsh");
        assertNotNull(anchor);
        var shaders = Path.of(anchor.toURI()).getParent().getParent();
        int checked = 0;
        try (var files = Files.walk(shaders)) {
            for (var file : files.filter(f -> f.toString().matches(".*\\.(vsh|fsh|glsl)")).toList()) {
                var code = Files.readString(file);
                var name = shaders.relativize(file).toString();
                if (name.endsWith(".glsl")) {
                    assertFalse(code.contains("#version"), name + ": an include has no #version");
                } else {
                    var lines = code.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
                    assertEquals("#version 330", lines.get(0), name);
                    assertEquals("#extension GL_ARB_separate_shader_objects : require", lines.get(1), name);
                }
                assertFalse(code.contains("#moj_import"), name + ": #include, not #moj_import");
                assertFalse(code.contains("gl_VertexID"), name + ": gl_VertexIndex, not gl_VertexID");
                checked++;
            }
        }
        assertTrue(checked > 40, "shaders checked: " + checked);
    }

    @Test
    void theScreenEffectsBlocksAreWhatTheirFramesPack() throws IOException {
        // FrameBlock writes the matrix, then the frame's vec4s: the include's block must take as much room
        var blocks = Map.of(
                "halo.glsl HaloInfo", HaloFrame.VECTORS,
                "rainbow.glsl RainbowInfo", RainbowFrame.VECTORS,
                "ground_fog.glsl GroundFogInfo", GroundFogFrame.VECTORS,
                "aurora_realistic.glsl RealisticAuroraInfo", RealisticAuroraFrame.VECTORS,
                "aurora_cinematic.glsl CinematicAuroraInfo", CinematicAuroraFrame.VECTORS);
        for (var entry : blocks.entrySet()) {
            var parts = entry.getKey().split(" ");
            var source = ShaderSources.read("include/" + parts[0]);
            assertEquals(new FrameBlock("test", entry.getValue()).size(), ShaderSources.blockSize(source, parts[1], Map.of()), entry.getKey());
        }
    }

    @Test
    void theNamesAPrecipitationShaderUsesAreDefined() throws IOException {
        // Their constants come from the pipeline (PrecipitationShaders): a name it doesn't give fails to compile
        var constant = Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\\b");
        var declared = Pattern.compile("(?:#define\\s+|const\\s+\\w+\\s+)([A-Z][A-Z0-9_]*)");
        for (var shader : PrecipitationShaders.SHADERS) {
            var pipeline = shader.pipeline();
            var defined = defined(pipeline);
            for (var type : List.of(ShaderType.VERTEX, ShaderType.FRAGMENT)) {
                var program = ShaderSources.program(pipeline.getShaders().get(type), type == ShaderType.VERTEX ? ".vsh" : ".fsh", defined);
                var known = new HashSet<>(defined);
                var declaration = declared.matcher(program);
                while (declaration.find())
                    known.add(declaration.group(1));
                var code = program.replaceAll("//[^\n]*", "");
                var use = constant.matcher(code);
                while (use.find())
                    assertTrue(known.contains(use.group()), pipeline.getLocation() + " uses " + use.group() + ", which nothing defines");
            }
        }
    }

    @Test
    void everyPrecipitationPipelineTestsDepth() {
        // A builder's default is no depth state: drops would be drawn over the hills in front of them
        for (var shader : PrecipitationShaders.SHADERS)
            assertNotNull(shader.pipeline().getDepthStencilState(), shader.pipeline().getLocation() + " has no depth state");
    }
}
