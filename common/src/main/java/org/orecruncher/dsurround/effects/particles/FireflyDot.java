// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.events.HandlerPriority;

import java.io.IOException;
import java.util.function.IntBinaryOperator;

/**
 * Where the game's firefly texture is lit, so a firefly can be drawn as the game draws its own, a small bright dot, with
 * the dot in the middle of its halo. The game's texture is 8 by 8 with one lit pixel, in a corner: drawn whole, the dot
 * would sit off to one side of the halo. Read from the texture, so a resource pack's firefly is found too; read again
 * after resources reload. Render thread.
 */
final class FireflyDot {

    static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/particle/firefly.png");
    // Fainter than this isn't part of the dot
    private static final int MIN_ALPHA = 16;

    /**
     * The lit part of the texture, as fractions of it, square about its middle so it isn't stretched
     */
    record Bounds(float u0, float v0, float u1, float v1) {
        static final Bounds WHOLE = new Bounds(0F, 0F, 1F, 1F);

        /**
         * Its width as a share of the texture's
         */
        float size() {
            return this.u1 - this.u0;
        }
    }

    private static volatile @Nullable Bounds bounds;

    static {
        IReloadEvent.EVENT.register((resources, scope) -> bounds = null, HandlerPriority.LOW);
    }

    private FireflyDot() {
    }

    /**
     * The lit part of the game's firefly texture; the whole of it if it can't be read or nothing in it is lit
     */
    static Bounds bounds() {
        var found = bounds;
        if (found == null) {
            found = read();
            bounds = found;
        }
        return found;
    }

    private static Bounds read() {
        var resource = Minecraft.getInstance().getResourceManager().getResource(TEXTURE);
        if (resource.isEmpty())
            return Bounds.WHOLE;
        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            // ARGB: alpha is the top byte
            return find(image.getWidth(), image.getHeight(), (x, y) -> (image.getPixel(x, y) >>> 24) & 0xFF);
        } catch (IOException | RuntimeException e) {
            Library.LOGGER.error(e, "Unable to read %s; fireflies are drawn with all of it", TEXTURE);
            return Bounds.WHOLE;
        }
    }

    /**
     * The smallest square about the lit pixels' middle that holds them all, as fractions of the texture; the whole
     * texture if none is lit
     */
    static Bounds find(int width, int height, IntBinaryOperator alphaAt) {
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (alphaAt.applyAsInt(x, y) >= MIN_ALPHA) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
        if (maxX < 0)
            return Bounds.WHOLE;
        int side = Math.max(maxX - minX, maxY - minY) + 1;
        // Square about the middle, kept inside the texture
        int x0 = Math.clamp((minX + maxX + 1 - side) / 2, 0, Math.max(0, width - side));
        int y0 = Math.clamp((minY + maxY + 1 - side) / 2, 0, Math.max(0, height - side));
        return new Bounds((float) x0 / width, (float) y0 / height, (float) Math.min(width, x0 + side) / width, (float) Math.min(height, y0 + side) / height);
    }
}
