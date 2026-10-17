package org.orecruncher.dsurround.gui.overlay.lightlevel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;

/**
 * The numbers 0 to 15, drawn once with the game's font into a texture, a 4 by 4 grid of cells: each a white number
 * with a black outline, on clear. The overlay draws a label as one quad from its cell, tinted by vertex color, rather
 * than as a quad per character of text. Drawn again after resources reload, so a resource pack's font shows, and when
 * the "force Unicode font" option changes.
 * <p>
 * Drawn on the client thread outside of rendering (from the tick), into a target of its own, and read back.
 */
final class LightLevelAtlas {

    static final ResourceLocation ID = Constants.asId("dynamic/light_levels");
    static final int CELLS = 4;
    static final int CELL_SIZE = 64;
    static final int SIZE = CELLS * CELL_SIZE;
    // Space left around a number in its cell, in texture pixels, so neighbours never bleed into it
    private static final int CELL_PADDING = 4;

    private DynamicTexture texture;
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
        return this.texture != null;
    }

    /**
     * Draws the numbers if the font has changed since they were last drawn. Called on the client thread, between
     * frames.
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
            var image = draw(GameUtils.getTextRenderer());
            if (this.texture == null) {
                this.texture = new DynamicTexture(image);
                GameUtils.getTextureManager().register(ID, this.texture);
            } else {
                this.texture.setPixels(image);
                this.texture.upload();
            }
        } catch (Throwable t) {
            // Tried again only after the next reload
            this.failed = true;
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
        return (value / CELLS) / (float) CELLS;
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

    private static NativeImage draw(Font font) {
        int widest = 0;
        for (int i = 0; i < 16; i++)
            widest = Math.max(widest, textWidth(font, i));
        int scale = fitScale(widest, font.lineHeight);

        RenderTarget target = new TextureTarget(SIZE, SIZE, false, Minecraft.ON_OSX);
        var projection = RenderSystem.getProjectionMatrix();
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try (var bytes = new ByteBufferBuilder(4096)) {
            target.setClearColor(0F, 0F, 0F, 0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0F, SIZE, SIZE, 0F, -1000F, 1000F), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();

            var buffers = MultiBufferSource.immediate(bytes);
            // The outlines first, in a batch of their own, so the numbers are drawn over them
            for (int i = 0; i < 16; i++) {
                var matrix = cellMatrix(font, i, scale);
                var text = Integer.toString(i);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        if (dx != 0 || dy != 0)
                            font.drawInBatch(text, dx, dy, 0xFF000000, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
            buffers.endBatch();
            for (int i = 0; i < 16; i++)
                font.drawInBatch(Integer.toString(i), 0, 0, 0xFFFFFFFF, false, cellMatrix(font, i, scale), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            buffers.endBatch();

            var image = new NativeImage(SIZE, SIZE, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            toBlackAndWhite(image);
            return image;
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            target.unbindWrite();
            target.destroyBuffers();
            GameUtils.getMC().getMainRenderTarget().bindWrite(true);
        }
    }

    /**
     * Where a number is drawn from: its cell, centered, at the scale
     */
    private static Matrix4f cellMatrix(Font font, int value, int scale) {
        // A font's width includes the space after the last character; its line height a pixel under the baseline
        float width = (textWidth(font, value) - 1) * scale;
        float height = (font.lineHeight - 1) * scale;
        float x = (value % CELLS) * CELL_SIZE + Math.round((CELL_SIZE - width) / 2F);
        float y = (value / CELLS) * CELL_SIZE + Math.round((CELL_SIZE - height) / 2F);
        return new Matrix4f().translate(x, y, 0F).scale(scale, scale, 1F);
    }

    private static int textWidth(Font font, int value) {
        return font.width(Integer.toString(value));
    }

    /**
     * Makes each drawn pixel pure white or black, keeping its coverage: the font's shader lights the text with the
     * light map, which can tint it, and the overlay tints the white itself
     */
    private static void toBlackAndWhite(NativeImage image) {
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int alpha = abgr >>> 24;
                if (alpha == 0)
                    continue;
                int brightest = Math.max(abgr & 0xFF, Math.max((abgr >> 8) & 0xFF, (abgr >> 16) & 0xFF));
                int level = brightest >= 128 ? 0xFF : 0;
                image.setPixelRGBA(x, y, alpha << 24 | level << 16 | level << 8 | level);
            }
    }
}
