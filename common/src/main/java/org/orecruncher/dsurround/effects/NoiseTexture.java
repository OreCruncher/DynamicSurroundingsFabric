package org.orecruncher.dsurround.effects;

import net.minecraft.client.renderer.texture.DynamicTexture;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * Noise textures for shaders: a random value a texel, in red, read smoothly between texels and repeating. A shader
 * reads smooth value noise from one with a single read, in place of working out the four corners of its cell:
 * <pre>
 * float noise(vec2 p) {
 *     vec2 cell = floor(p);
 *     vec2 f = fract(p);
 *     f = f * f * (3.0 - 2.0 * f);
 *     return texture(NoiseSampler, (cell + f + 0.5) / NOISE_SIZE).r;
 * }
 * </pre>
 * Must be made on the render thread.
 */
public final class NoiseTexture {

    private NoiseTexture() {
    }

    /**
     * A noise texture {@code size} texels each way, the same each time for a seed
     */
    public static DynamicTexture create(int size, long seed) {
        var texture = new DynamicTexture(size, size, false);
        var pixels = texture.getPixels();
        var random = Randomizer.create(seed);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int value = random.nextInt(256);
                pixels.setPixelRGBA(i, j, 0xFF000000 | value);
            }
        }
        texture.upload();
        texture.setFilter(true, false);
        return texture;
    }
}
