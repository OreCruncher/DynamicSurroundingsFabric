package org.orecruncher.dsurround.gui.overlay.lightlevel;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.Transforms;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalInt;

/**
 * The numbers 0 to 15, drawn once with the game's font into a texture, a 4 by 4 grid of cells: each a white number
 * with a black outline, on clear. The overlay draws a label as one quad from its cell, tinted by vertex color, rather
 * than as a quad per character of text. Drawn again after resources reload, so a resource pack's font shows, and when
 * the "force Unicode font" option changes.
 * <p>
 * The font's glyphs are drawn straight into the texture on the GPU, with the pipelines the game draws them with on
 * screen: first every outline in black, then every number in white over them. The texture is registered with the
 * texture manager as {@link #ID}, so the labels are drawn with vanilla's text render type. Everything here runs on the
 * render thread.
 */
final class LightLevelAtlas {

    static final Identifier ID = Constants.asId("dynamic/light_levels");
    static final int CELLS = 4;
    static final int CELL_SIZE = 64;
    static final int SIZE = CELLS * CELL_SIZE;
    // Space left around a number in its cell, in texture pixels, so neighbours never bleed into it
    private static final int CELL_PADDING = 4;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int CLEAR = 0;

    private AtlasTexture texture;
    private CachedOrthoProjectionMatrixBuffer projection;
    private boolean drawn;
    private boolean stale = true;
    private boolean failed;
    private boolean unicodeFont;

    /**
     * The fonts have changed: draw the numbers again when next asked
     */
    void invalidate() {
        this.stale = true;
        this.failed = false;
    }

    /**
     * Whether the texture can be drawn from
     */
    boolean isReady() {
        return this.drawn;
    }

    /**
     * Draws the numbers if the font has changed since they were last drawn. Called on the render thread, outside any
     * render pass.
     */
    void refresh() {
        boolean unicode = GameUtils.getGameSettings().forceUnicodeFont().get();
        if (unicode != this.unicodeFont) {
            this.unicodeFont = unicode;
            this.invalidate();
        }
        if (!this.stale || this.failed)
            return;
        this.stale = false;

        try {
            if (this.texture == null) {
                this.texture = new AtlasTexture();
                GameUtils.getTextureManager().register(ID, this.texture);
                this.projection = new CachedOrthoProjectionMatrixBuffer("Dynamic Surroundings light levels", -1000F, 1000F, true);
            }
            this.draw(GameUtils.getTextRenderer());
            this.drawn = true;
        } catch (Throwable t) {
            // Tried again only after the next reload
            this.failed = true;
            this.drawn = false;
            Library.LOGGER.error(t, "Unable to draw the light level numbers");
        }
    }

    /**
     * The left of a number's cell, as a fraction of the texture
     */
    static float cellU(int value) {
        return (value & (CELLS - 1)) / (float) CELLS;
    }

    /**
     * The top of a number's cell, as a fraction of the texture
     */
    static float cellV(int value) {
        int row = value / CELLS;
        return row / (float) CELLS;
    }

    /**
     * A texture coordinate down the atlas as drawn (0 at the top of the first row of cells) as it is sampled. A texture
     * drawn into is stored bottom row first, as the game's own drawn-into textures are (see PictureInPictureRenderer's
     * blit), so the first row of cells is at the far end.
     */
    static float sampledV(float v) {
        return 1F - v;
    }

    /**
     * The whole number of texture pixels per font pixel that fits the widest number, outline and all, in a cell
     */
    static int fitScale(int widestText, int lineHeight) {
        int room = CELL_SIZE - 2 * CELL_PADDING;
        // The outline adds a pixel either side
        int scale = Math.min(room / (widestText + 2), room / (lineHeight + 2));
        return Math.max(1, scale);
    }

    private void draw(Font font) {
        int widest = 0;
        for (int i = 0; i < 16; i++)
            widest = Math.max(widest, textWidth(font, i));
        int scale = fitScale(widest, font.lineHeight);

        // The outlines: the number in black, a font pixel off each way
        var outlines = new ArrayList<Glyph>();
        for (int i = 0; i < 16; i++) {
            var matrix = cellMatrix(font, i, scale);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    if (dx != 0 || dy != 0)
                        collect(font, i, dx, dy, BLACK, matrix, outlines);
        }
        var numbers = new ArrayList<Glyph>();
        for (int i = 0; i < 16; i++)
            collect(font, i, 0, 0, WHITE, cellMatrix(font, i, scale), numbers);

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(this.texture.getTexture(), CLEAR);
        var projection = this.projection.getBuffer(SIZE, SIZE);
        var transforms = Transforms.of(new Matrix4f());
        try (var bytes = new ByteBufferBuilder(4096)) {
            // The outlines first, so the numbers are drawn over them
            this.drawGlyphs(outlines, bytes, projection, transforms);
            this.drawGlyphs(numbers, bytes, projection, transforms);
        }
    }

    private static void collect(Font font, int value, int dx, int dy, int color, Matrix4f matrix, List<Glyph> into) {
        font.prepareText(Integer.toString(value), dx, dy, color, false, 0).visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled glyph) {
                into.add(new Glyph(glyph, matrix));
            }
        });
    }

    /**
     * Draws glyphs into the atlas, a batch for each of the font's textures they come from
     */
    private void drawGlyphs(List<Glyph> glyphs, ByteBufferBuilder bytes, GpuBufferSlice projection,
                            GpuBufferSlice transforms) {
        var batches = new LinkedHashMap<Batch, List<Glyph>>();
        for (var glyph : glyphs)
            batches.computeIfAbsent(new Batch(glyph.renderable.guiPipeline(), glyph.renderable.textureView()), k -> new ArrayList<>()).add(glyph);

        var device = RenderSystem.getDevice();
        for (var batch : batches.entrySet()) {
            var pipeline = batch.getKey().pipeline();
            VertexFormat format = pipeline.getVertexFormat();
            var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (var glyph : batch.getValue())
                glyph.renderable.render(glyph.matrix, builder, 0xF000F0, true);
            try (var mesh = builder.build()) {
                if (mesh == null)
                    continue;
                int indexCount = mesh.drawState().indexCount();
                var vertices = mesh.vertexBuffer();
                try (GpuBuffer vertexBuffer = device.createBuffer(() -> "Dynamic Surroundings light level glyphs", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices.remaining())) {
                    device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(), vertices);
                    var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    var indexBuffer = indices.getBuffer(indexCount);
                    try (RenderPass pass = device.createCommandEncoder()
                            .createRenderPass(() -> "Dynamic Surroundings light level numbers", this.texture.getTextureView(), OptionalInt.empty())) {
                        pass.setPipeline(pipeline);
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("Projection", projection);
                        pass.setUniform("DynamicTransforms", transforms);
                        pass.bindTexture("Sampler0", batch.getKey().texture(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                        pass.setVertexBuffer(0, vertexBuffer);
                        pass.setIndexBuffer(indexBuffer, indices.type());
                        pass.drawIndexed(0, 0, indexCount, 1);
                    }
                }
            }
        }
    }

    /**
     * Where a number is drawn from: its cell, centered, at the scale
     */
    private static Matrix4f cellMatrix(Font font, int value, int scale) {
        // A font's width includes the space after the last character; its line height a pixel under the baseline
        float width = (textWidth(font, value) - 1) * scale;
        float height = (font.lineHeight - 1) * scale;
        int column = value % CELLS;
        int row = value / CELLS;
        float x = column * CELL_SIZE + Math.round((CELL_SIZE - width) / 2F);
        float y = row * CELL_SIZE + Math.round((CELL_SIZE - height) / 2F);
        return new Matrix4f().translate(x, y, 0F).scale(scale, scale, 1F);
    }

    private static int textWidth(Font font, int value) {
        return font.width(Integer.toString(value));
    }

    private record Glyph(TextRenderable renderable, Matrix4f matrix) {
    }

    private record Batch(RenderPipeline pipeline, GpuTextureView texture) {
    }

    /**
     * The atlas as the texture manager holds it: a texture the numbers are drawn into, and read from up close and
     * far away alike without blurring
     */
    private static final class AtlasTexture extends AbstractTexture {
        AtlasTexture() {
            var device = RenderSystem.getDevice();
            this.texture = device.createTexture(() -> "Dynamic Surroundings light level numbers",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8, SIZE, SIZE, 1, 1);
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }
}
