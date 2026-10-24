package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * The snowflakes of the game's own snow texture, the one its sheets of falling snow are drawn with, so a resource
 * pack's snow is the snow particles fall as too. The texture is scanned for flakes: each separate group of visible
 * pixels is one, and is drawn on its own.
 * <p>
 * Looked up the first time they are wanted after resources load. Render thread only.
 */
public final class Snowflakes {

    /**
     * The game's snow texture
     */
    public static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/environment/snow.png");

    // Groups of pixels wider or taller than this aren't taken for flakes; nor are more than this many flakes kept
    static final int MAX_FLAKE_SIZE = 16;
    static final int MAX_FLAKES = 64;
    // Pixels this transparent or more aren't part of a flake
    static final int MIN_ALPHA = 16;

    /**
     * Where a flake is in the texture, in texture coordinates, and its size in pixels: the larger of its width and
     * height. Square, so the flake isn't stretched when drawn.
     */
    public record Flake(float u0, float v0, float u1, float v1, int size) {
    }

    private final IModLog logger;
    private List<Flake> flakes = List.of();
    private int largest = 1;
    private boolean loaded;

    public Snowflakes(IModLog logger) {
        this.logger = logger;
    }

    /**
     * The flakes, looked up if resources have loaded since the last time; empty if the texture has none
     */
    public List<Flake> flakes(ResourceManager resources) {
        if (!this.loaded) {
            this.loaded = true;
            this.flakes = this.load(resources);
            this.largest = this.flakes.stream().mapToInt(Flake::size).max().orElse(1);
        }
        return this.flakes;
    }

    /**
     * The size of the largest flake, in pixels, so the others can be drawn in proportion to it
     */
    public int largest() {
        return this.largest;
    }

    /**
     * Resources have loaded again: the flakes are looked up afresh when next wanted
     */
    public void reload() {
        this.loaded = false;
    }

    private List<Flake> load(ResourceManager resources) {
        var resource = resources.getResource(TEXTURE);
        if (resource.isEmpty()) {
            this.logger.warn("No %s; snow will fall as plain flakes", TEXTURE);
            return List.of();
        }
        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            // ARGB: alpha is the top byte
            var found = find(image.getWidth(), image.getHeight(), (x, y) -> (image.getPixel(x, y) >>> 24) & 0xFF);
            if (found.isEmpty())
                this.logger.warn("Found no snowflakes in %s; snow will fall as plain flakes", TEXTURE);
            return found;
        } catch (IOException | RuntimeException e) {
            this.logger.error(e, "Unable to read %s; snow will fall as plain flakes", TEXTURE);
            return List.of();
        }
    }

    /**
     * Finds the flakes in a picture: each group of visible pixels touching one another, side or corner, that is no
     * larger than {@link #MAX_FLAKE_SIZE} each way.
     *
     * @param alphaAt the alpha of the pixel at (x, y), 0 to 255
     */
    static List<Flake> find(int width, int height, IntBinaryOperator alphaAt) {
        var seen = new boolean[width * height];
        var flakes = new ArrayList<Flake>();
        var stack = new int[width * height];
        for (int y = 0; y < height && flakes.size() < MAX_FLAKES; y++) {
            for (int x = 0; x < width && flakes.size() < MAX_FLAKES; x++) {
                if (seen[y * width + x] || alphaAt.applyAsInt(x, y) < MIN_ALPHA)
                    continue;

                // Gather the group, keeping its bounds
                int minX = x, maxX = x, minY = y, maxY = y;
                int top = 0;
                stack[top++] = y * width + x;
                seen[y * width + x] = true;
                while (top > 0) {
                    int at = stack[--top];
                    int px = at % width, py = at / width;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = px + dx, ny = py + dy;
                            if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                                continue;
                            int n = ny * width + nx;
                            if (!seen[n] && alphaAt.applyAsInt(nx, ny) >= MIN_ALPHA) {
                                seen[n] = true;
                                stack[top++] = n;
                            }
                        }
                    }
                }

                int w = maxX - minX + 1, h = maxY - minY + 1;
                if (w > MAX_FLAKE_SIZE || h > MAX_FLAKE_SIZE)
                    continue;
                // Square about the flake's middle, so drawing it on a square doesn't stretch it
                int size = Math.max(w, h);
                float left = minX - (size - w) / 2F, top0 = minY - (size - h) / 2F;
                flakes.add(new Flake(left / width, top0 / height, (left + size) / width, (top0 + size) / height, size));
            }
        }
        return flakes;
    }
}
